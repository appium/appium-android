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
  private jpegSeq = 0;
  private lastJpeg?: MediaUnit;
  private lastJpegEmitAt = 0;
  private size?: {width: number; height: number};
  private dropped = 0;
  private heartbeat?: NodeJS.Timeout;
  private pump?: Promise<void>;
  // Whether the device-side session is ours to stop (false if another client's stream was found running)
  private owned = false;
  private announced = false;
  private endReason = 'ended';
  private firstUnitSeen!: () => void;
  private readonly firstUnit = new Promise<void>((resolve) => (this.firstUnitSeen = resolve));
  private stopped = false;
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

  async start(): Promise<MediaStreamInfo> {
    const {opts} = this;
    // A start() that throws may still have brought the on-device service up, so it is ours
    this.owned = true;
    const started =
      opts.kind === 'video'
        ? await (this.session as VideoStreamSession).start({
            codec: opts.codec,
            fps: opts.fps,
            bitrate: opts.bitrate,
            resolution: opts.resolution,
            audio: opts.audio,
          })
        : await (this.session as JpegStreamSession).start({fps: opts.fps, quality: opts.quality, scale: opts.scale});
    if (!started) {
      // The device already runs a stream of this kind that is not ours: leave it alone
      this.owned = false;
      throw new Error(`Another ${opts.kind} stream is already running on the device`);
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

  /** Idempotent; resolves once the stream is torn down and its pump has finished. */
  async stop(reason = 'stopped'): Promise<void> {
    await this.finish(reason);
    await this.pump?.catch(() => {});
  }

  // Must not await the pump: it is also called from inside the pump itself
  private async finish(reason: string): Promise<void> {
    if (this.stopped) {
      return;
    }
    this.stopped = true;
    clearInterval(this.heartbeat);
    this.batcher.close();
    this.deps.emitter.off(BACKPRESSURE_EVENT_NAME, this.onBackpressure);
    if (this.owned) {
      try {
        await this.session.stop();
      } catch (e) {
        this.deps.log.warn(`Cannot stop the ${this.kind} stream session: ${(e as Error).message}`);
      }
    }
    if (this.announced) {
      this.deps.emit({method: MEDIA_ENDED_EVENT, params: {streamId: this.streamId, reason}});
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

  private requestKeyFrame(): void {
    const now = Date.now();
    if (this.kind !== 'video' || now - this.lastKeyFrameRequestAt < KEYFRAME_REQUEST_MIN_INTERVAL_MS) {
      return;
    }
    this.lastKeyFrameRequestAt = now;
    void this.sendKeyFrameRequest();
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
