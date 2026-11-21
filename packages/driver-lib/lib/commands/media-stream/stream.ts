import {randomUUID} from 'node:crypto';
import type {EventEmitter} from 'node:events';

import type {AppiumLogger, BiDiBackpressureEvent, BiDiEvent} from '@appium/types';
import type {AccessUnit, JpegFrame, JpegStreamSession, VideoStreamSession} from 'io.appium.settings';

import {UnitBatcher} from './batcher.js';
import {
  AUDIO_CHANNELS,
  AUDIO_SAMPLE_RATE,
  BACKPRESSURE_EVENT_NAME,
  FIRST_UNIT_TIMEOUT_MS,
  KEYFRAME_REQUEST_MIN_INTERVAL_MS,
  MEDIA_ENDED_EVENT,
  MEDIA_FORMAT_CHANGED_EVENT,
  MEDIA_STARTED_EVENT,
  MEDIA_STREAM_MODULE,
  MEDIA_UNITS_EVENT,
} from './constants.js';
import {readJpegSize} from './jpeg.js';
import {parseVideoSize} from './sps.js';
import type {MediaStreamInfo, MediaUnit, StartMediaStreamOpts, WireUnit} from './types.js';

export interface MediaStreamDeps {
  emit: (event: BiDiEvent) => void;
  emitter: EventEmitter;
  hasSubscribers: () => boolean;
  log: AppiumLogger;
  onEnded: (streamId: string) => void;
}

/**
 * Owns one settings-app stream session and republishes its units as
 * `appium:mediaStream.*` BiDi events (config inlined, droppable hints).
 */
export class MediaStream {
  readonly streamId = randomUUID();
  readonly kind: StartMediaStreamOpts['kind'];

  private readonly batcher: UnitBatcher;
  private config?: Buffer;
  private configCount = 0;
  private videoSynced = false;
  private wasSubscribed = false;
  private lastKeyFrameRequestAt = 0;
  private keyFrameRetry?: NodeJS.Timeout;
  private jpegSeq = 0;
  private lastJpeg?: MediaUnit;
  private lastJpegEmitAt = 0;
  private size?: {width: number; height: number};
  private dropped = 0;
  private heartbeat?: NodeJS.Timeout;
  private pump?: Promise<void>;
  /**
   * Settles once the device session is up or failed to start, telling whether there is a device
   * session of ours to stop. False if another client's stream of this kind was found running, or if
   * the options were rejected (TypeError, RangeError) before the device was touched. Any other
   * failure may have brought the on-device service up, so it counts as ours.
   */
  private deviceStart?: Promise<boolean>;
  private announced = false;
  private endReason = 'ended';
  private firstUnitSeen!: () => void;
  private readonly firstUnit = new Promise<void>((resolve) => (this.firstUnitSeen = resolve));
  private stopped = false;
  private finished?: Promise<void>;
  private get videoChain(): string {
    return `${this.streamId}:video`;
  }

  constructor(
    private readonly session: VideoStreamSession | JpegStreamSession,
    private readonly opts: StartMediaStreamOpts & {batchMs: number; idleHeartbeatMs: number},
    private readonly deps: MediaStreamDeps,
  ) {
    this.kind = opts.kind;
    this.batcher = new UnitBatcher(opts.kind === 'jpeg' ? 0 : opts.batchMs, (units) => this.emitUnits(units));
  }

  /**
   * Starts the device session and republishes its units.
   * Throws if another client's stream of this kind runs on the device, which is left alone.
   * If the stream is stopped while the device session is starting, this throws and the teardown
   * stops the device session once it is up.
   */
  async start(): Promise<MediaStreamInfo> {
    const {opts} = this;
    if (this.stopped) {
      throw new Error('The stream was stopped before it started');
    }
    const starting =
      opts.kind === 'video'
        ? (this.session as VideoStreamSession).start({
            codec: opts.codec,
            fps: opts.fps,
            bitrate: opts.bitrate,
            resolution: opts.resolution,
            audio: opts.audio,
          })
        : (this.session as JpegStreamSession).start({fps: opts.fps, quality: opts.quality, scale: opts.scale});
    this.deviceStart = (async () => {
      try {
        return await starting;
      } catch (e) {
        return !(e instanceof TypeError || e instanceof RangeError);
      }
    })();
    if (!(await starting)) {
      throw new Error(`Another ${opts.kind} stream is already running on the device`);
    }
    if (this.stopped) {
      throw new Error('The stream was stopped while it was starting');
    }

    this.deps.emitter.on(BACKPRESSURE_EVENT_NAME, this.onBackpressure);
    if (opts.kind === 'jpeg' && opts.idleHeartbeatMs > 0) {
      this.heartbeat = setInterval(() => this.replayLastJpeg(), opts.idleHeartbeatMs);
    }
    this.pump = this.runPump();
    if (opts.kind === 'jpeg') {
      // A static screen yields no frames, so there is nothing to wait for
      this.announce();
    } else {
      await this.waitForFirstUnit(this.pump);
    }
    return this.info();
  }

  // Fails fast if the device encoder rejected the configuration (the on-device session
  // then ends without any output) or never produced anything
  private async waitForFirstUnit(pump: Promise<void>): Promise<void> {
    let timer: NodeJS.Timeout | undefined;
    const hint =
      'The device encoder may not support the requested configuration, check the logcat of io.appium.settings.';
    try {
      await Promise.race([
        this.firstUnit,
        new Promise<never>((_resolve, reject) => {
          timer = setTimeout(
            () =>
              reject(
                new Error(`The ${this.kind} stream produced no output within ${FIRST_UNIT_TIMEOUT_MS}ms. ${hint}`),
              ),
            FIRST_UNIT_TIMEOUT_MS,
          );
        }),
        pump.then(() => {
          throw new Error(`The ${this.kind} stream ended before producing any output (${this.endReason}). ${hint}`);
        }),
      ]);
    } finally {
      clearTimeout(timer);
    }
  }

  private announce(): void {
    if (this.announced || this.stopped) {
      return;
    }
    this.announced = true;
    this.deps.emit({method: MEDIA_STARTED_EVENT, params: {...this.info()}});
  }

  get isStopped(): boolean {
    return this.stopped;
  }

  /**
   * Idempotent: every call waits for the same teardown, and resolves once the device session of
   * the stream is stopped (after its start settled, if it was still starting) and the pump has
   * finished. Never rejects.
   */
  async stop(reason = 'stopped'): Promise<void> {
    await this.finish(reason);
    await this.pump?.catch(() => {});
  }

  /**
   * Marks the stream stopped and returns its single teardown.
   * Must not await the pump: it is also called from inside the pump itself.
   */
  private finish(reason: string): Promise<void> {
    this.stopped = true;
    return (this.finished ??= this.teardown(reason));
  }

  /**
   * Waits for a device start that is still in flight (a starting device session can only be
   * stopped once it is up), stops the device session if it is ours, emits `ended`, and calls
   * `onEnded`. Never rejects: the stream is ended whatever its device session or a listener of
   * `ended` does, so failures of either are logged, not passed to the callers of stop().
   * It awaits before running any callback, so finish() has stored it by the time a callback stops
   * the stream again, and that call gets this same teardown.
   */
  private async teardown(reason: string): Promise<void> {
    clearInterval(this.heartbeat);
    this.cancelKeyFrameRetry();
    this.batcher.close();
    this.deps.emitter.off(BACKPRESSURE_EVENT_NAME, this.onBackpressure);
    if (await this.deviceStart) {
      try {
        await this.session.stop();
      } catch (e) {
        this.deps.log.warn(`Cannot stop the ${this.kind} stream session: ${(e as Error).message}`);
      }
    }
    if (this.announced) {
      try {
        this.deps.emit({method: MEDIA_ENDED_EVENT, params: {streamId: this.streamId, reason}});
      } catch (e) {
        this.deps.log.warn(`Cannot emit the end of the ${this.kind} stream: ${(e as Error).message}`);
      }
    }
    this.deps.onEnded(this.streamId);
  }

  private info(): MediaStreamInfo {
    const {opts} = this;
    const isVideo = opts.kind === 'video';
    return {
      streamId: this.streamId,
      kind: opts.kind,
      tracks: isVideo ? (opts.audio ? ['video', 'audio'] : ['video']) : ['jpeg'],
      codec: isVideo ? (opts.codec ?? 'h264') : 'jpeg',
      event: MEDIA_STREAM_MODULE,
      ...this.size,
      ...(isVideo && opts.audio ? {audio: {sampleRate: AUDIO_SAMPLE_RATE, channels: AUDIO_CHANNELS}} : {}),
    };
  }

  private async runPump(): Promise<void> {
    let reason = 'ended';
    try {
      if (this.kind === 'video') {
        for await (const au of (this.session as VideoStreamSession).accessUnits()) {
          this.onAccessUnit(au);
        }
      } else {
        for await (const frame of (this.session as JpegStreamSession).frames()) {
          this.onJpegFrame(frame);
        }
      }
    } catch (e) {
      reason = `error: ${(e as Error).message}`;
    }
    this.endReason = reason;
    await this.finish(reason);
  }

  private onAccessUnit(au: AccessUnit): void {
    if (this.stopped) {
      return;
    }
    if (au.track === 'video' && au.isConfig) {
      // Config is never forwarded; it is prepended to every keyframe instead
      this.config = au.data;
      // A restarted encoder needs a keyframe of its own before deltas are forwarded
      this.videoSynced = false;
      this.onVideoConfig(au.data);
    }
    if (au.track === 'audio' && !this.announced) {
      // Audio may precede the video config; `started` must carry the video size
      return;
    }
    // The config unit counts too: it is the first thing the encoder reports
    this.announce();
    this.firstUnitSeen();
    if (au.track === 'video' && au.isConfig) {
      return;
    }
    const subscribed = this.deps.hasSubscribers();
    if (subscribed && !this.wasSubscribed) {
      // Late joiner: keyframes are rare on a slowly changing screen, so ask for one
      this.requestKeyFrame();
    }
    this.wasSubscribed = subscribed;
    if (!subscribed) {
      this.videoSynced = false;
      return;
    }
    const unit: MediaUnit = {
      track: au.track,
      seq: au.sequence,
      ts: au.timestampMicros,
      key: au.track === 'audio' ? true : au.isKeyFrame,
      data: au.data,
    };
    if (au.track === 'video') {
      if (au.isKeyFrame) {
        this.videoSynced = true;
        // The subscribers get a keyframe now, so a request still waiting for the rate limit is not needed
        this.cancelKeyFrameRetry();
        unit.data = this.config ? Buffer.concat([this.config, au.data]) : au.data;
      } else if (!this.videoSynced) {
        return;
      }
    }
    this.batcher.push(unit);
  }

  // The first config sets the size announced in `started`; later ones are a restart of the
  // encoder (rotation), announced with the new size if it can be read from the SPS
  private onVideoConfig(config: Buffer): void {
    const size = parseVideoSize(config, this.opts.codec ?? 'h264');
    const isFirst = this.configCount++ === 0;
    const changed = !size || size.width !== this.size?.width || size.height !== this.size?.height;
    if (size) {
      this.size = size;
    }
    if (!isFirst) {
      // Units of the previous format must reach clients before the new format is announced
      this.batcher.flush();
    }
    if (!isFirst && changed) {
      this.deps.emit({method: MEDIA_FORMAT_CHANGED_EVENT, params: {streamId: this.streamId, ...size}});
    }
  }

  private onJpegFrame(frame: JpegFrame): void {
    if (this.stopped) {
      return;
    }
    const unit: MediaUnit = {
      track: 'jpeg',
      seq: this.jpegSeq++,
      ts: frame.timestampMicros,
      key: true,
      data: frame.data,
    };
    this.lastJpeg = unit;
    this.checkSize(frame.data);
    if (this.deps.hasSubscribers()) {
      this.lastJpegEmitAt = Date.now();
      this.batcher.push(unit);
    }
  }

  private replayLastJpeg(): void {
    if (
      this.stopped ||
      !this.lastJpeg ||
      Date.now() - this.lastJpegEmitAt < this.opts.idleHeartbeatMs ||
      !this.deps.hasSubscribers()
    ) {
      return;
    }
    this.lastJpegEmitAt = Date.now();
    this.batcher.push({...this.lastJpeg, seq: this.jpegSeq++});
  }

  private checkSize(jpeg: Buffer): void {
    const size = readJpegSize(jpeg);
    if (!size || (size.width === this.size?.width && size.height === this.size?.height)) {
      return;
    }
    this.size = size;
    this.deps.emit({method: MEDIA_FORMAT_CHANGED_EVENT, params: {streamId: this.streamId, ...size}});
  }

  private emitUnits(units: MediaUnit[]): void {
    const [first] = units;
    const delivery: BiDiEvent['delivery'] =
      first.track === 'audio'
        ? {policy: 'reliable'}
        : first.track === 'jpeg'
          ? {policy: 'droppable'}
          : {policy: 'droppable', chain: this.videoChain, syncPoint: first.key};
    const dropped = this.dropped;
    this.dropped = 0;
    this.deps.emit({
      method: MEDIA_UNITS_EVENT,
      params: {streamId: this.streamId, units: units.map(toWireUnit), dropped},
      delivery,
    });
  }

  // At most one request per interval. A request that comes sooner is sent once the interval has passed,
  // unless a keyframe reaches the subscribers first
  private requestKeyFrame(): void {
    if (this.stopped || this.kind !== 'video') {
      return;
    }
    const wait = this.lastKeyFrameRequestAt + KEYFRAME_REQUEST_MIN_INTERVAL_MS - Date.now();
    if (wait > 0) {
      this.keyFrameRetry ??= setTimeout(() => {
        this.keyFrameRetry = undefined;
        if (this.deps.hasSubscribers()) {
          this.requestKeyFrame();
        }
      }, wait);
      return;
    }
    this.cancelKeyFrameRetry();
    this.lastKeyFrameRequestAt = Date.now();
    void this.sendKeyFrameRequest();
  }

  private cancelKeyFrameRetry(): void {
    clearTimeout(this.keyFrameRetry);
    this.keyFrameRetry = undefined;
  }

  private async sendKeyFrameRequest(): Promise<void> {
    try {
      const sent = await (this.session as VideoStreamSession).requestKeyFrame();
      this.deps.log.debug(`Keyframe request for stream ${this.streamId}: ${sent ? 'sent' : 'unsupported'}`);
    } catch (e) {
      this.deps.log.debug(`Keyframe request for stream ${this.streamId} failed: ${(e as Error).message}`);
    }
  }

  private onBackpressure = (ev: BiDiBackpressureEvent): void => {
    // The shared emitter reports all streams: video drops carry its chain, JPEG ones have none
    if (ev.method !== MEDIA_UNITS_EVENT || ev.chain !== (this.kind === 'video' ? this.videoChain : undefined)) {
      return;
    }
    // Core skips the video chain until a sync point; get one without waiting for the GOP
    this.requestKeyFrame();
    this.dropped = Math.max(this.dropped, ev.dropped);
    this.deps.log.debug(`BiDi backpressure for stream ${this.streamId}: ${ev.state} (dropped=${ev.dropped})`);
  };
}

function toWireUnit(u: MediaUnit): WireUnit {
  return {track: u.track, seq: u.seq, ts: u.ts, key: u.key, data: u.data.toString('base64')};
}
