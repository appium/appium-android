import {randomUUID} from 'node:crypto';
import type {EventEmitter} from 'node:events';

import type {AppiumLogger, BiDiBackpressureEvent, BiDiEvent} from '@appium/types';
import type {AccessUnit, JpegFrame, JpegStreamSession, VideoStreamSession} from 'io.appium.settings';

import {UnitBatcher} from './batcher.js';
import {
  AUDIO_CHANNELS,
  AUDIO_SAMPLE_RATE,
  BACKPRESSURE_EVENT_NAME,
  KEYFRAME_REQUEST_MIN_INTERVAL_MS,
  MEDIA_ENDED_EVENT,
  MEDIA_FORMAT_CHANGED_EVENT,
  MEDIA_STARTED_EVENT,
  MEDIA_STREAM_MODULE,
  MEDIA_UNITS_EVENT,
} from './constants.js';
import {readJpegSize} from './jpeg.js';
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
  private stopped = false;

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
      throw new Error(`Another ${opts.kind} stream is already running on the device`);
    }

    this.deps.emitter.on(BACKPRESSURE_EVENT_NAME, this.onBackpressure);
    const info = this.info();
    this.deps.emit({method: MEDIA_STARTED_EVENT, params: {...info}});
    if (opts.kind === 'jpeg' && opts.idleHeartbeatMs > 0) {
      this.heartbeat = setInterval(() => this.replayLastJpeg(), opts.idleHeartbeatMs);
    }
    this.pump = this.runPump();
    return info;
  }

  async stop(reason = 'stopped'): Promise<void> {
    if (this.stopped) {
      return;
    }
    this.stopped = true;
    clearInterval(this.heartbeat);
    this.batcher.close();
    this.deps.emitter.off(BACKPRESSURE_EVENT_NAME, this.onBackpressure);
    try {
      await this.session.stop();
    } catch (e) {
      this.deps.log.warn(`Cannot stop the ${this.kind} stream session: ${(e as Error).message}`);
    }
    this.deps.emit({method: MEDIA_ENDED_EVENT, params: {streamId: this.streamId, reason}});
    this.deps.onEnded(this.streamId);
    await this.pump?.catch(() => {});
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
    await this.stop(reason);
  }

  private onAccessUnit(au: AccessUnit): void {
    if (au.track === 'video' && au.isConfig) {
      // Config is never forwarded; it is prepended to every keyframe instead
      if (++this.configCount > 1) {
        this.deps.emit({method: MEDIA_FORMAT_CHANGED_EVENT, params: {streamId: this.streamId}});
      }
      this.config = au.data;
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

  private onJpegFrame(frame: JpegFrame): void {
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
    if (!this.lastJpeg || Date.now() - this.lastJpegEmitAt < this.opts.idleHeartbeatMs || !this.deps.hasSubscribers()) {
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
          : {policy: 'droppable', chain: `${this.streamId}:video`, syncPoint: first.key};
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
    (this.session as VideoStreamSession).requestKeyFrame().then(
      (sent) => this.deps.log.debug(`Keyframe request for stream ${this.streamId}: ${sent ? 'sent' : 'unsupported'}`),
      (e) => this.deps.log.debug(`Keyframe request for stream ${this.streamId} failed: ${(e as Error).message}`),
    );
  }

  private onBackpressure = (ev: BiDiBackpressureEvent): void => {
    if (ev.method !== MEDIA_UNITS_EVENT) {
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
