import {errors} from 'appium/driver.js';

import type {AndroidDriverConstraints} from '../../constraints.js';
import type {AndroidDriver} from '../../driver.js';
import {BIDI_EVENT_NAME} from '../bidi/constants.js';
import {getCapabilities, queryVideoEncoders} from './capabilities.js';
import {
  DEFAULT_BATCH_MS,
  DEFAULT_IDLE_HEARTBEAT_MS,
  MEDIA_STREAM_MODULE,
  MEDIA_UNITS_EVENT,
  MIN_API_LEVEL,
} from './constants.js';
import {MediaStream} from './stream.js';
import type {MediaKind, MediaStreamCapabilities, MediaStreamInfo, StartMediaStreamOpts} from './types.js';

declare module '../../driver.js' {
  interface AndroidDriver<C extends AndroidDriverConstraints> {
    mobileStartMediaStream(
      ...args: Parameters<typeof mobileStartMediaStream>
    ): ReturnType<typeof mobileStartMediaStream>;
    mobileGetMediaStreamCapabilities(
      ...args: Parameters<typeof mobileGetMediaStreamCapabilities>
    ): ReturnType<typeof mobileGetMediaStreamCapabilities>;
    mobileStopMediaStream(...args: Parameters<typeof mobileStopMediaStream>): ReturnType<typeof mobileStopMediaStream>;
  }
}

/**
 * Starts streaming the screen as `appium:mediaStream.*` BiDi events.
 * Subscribe to `appium:mediaStream` before calling this. The result and the `started` event of a
 * video stream carry the frame size (after any scaling to the encoder's limits).
 *
 * @param kind 'video' (H.264/HEVC, optionally with AAC audio) or 'jpeg'.
 * @param codec Video codec, 'h264' by default.
 * @param fps Requested frames per second.
 * @param bitrate Video bitrate in bits per second.
 * @param quality JPEG quality, 1-100.
 * @param scale JPEG scale in percent, 1-100.
 * @param resolution Video resolution as 'WxH'.
 * @param audio Whether to add an AAC audio track (video only).
 * @param batchMs Units batching window for video/audio. 40 by default.
 * @param idleHeartbeatMs Re-send the last JPEG after this idle time. 1000 by default, 0 disables.
 */
export async function mobileStartMediaStream(
  this: AndroidDriver,
  kind: MediaKind,
  codec?: 'h264' | 'hevc',
  fps?: number,
  bitrate?: number,
  quality?: number,
  scale?: number,
  resolution?: string,
  audio?: boolean,
  batchMs: number = DEFAULT_BATCH_MS,
  idleHeartbeatMs: number = DEFAULT_IDLE_HEARTBEAT_MS,
): Promise<MediaStreamInfo> {
  const opts: StartMediaStreamOpts & {batchMs: number; idleHeartbeatMs: number} = {
    kind,
    codec,
    fps,
    bitrate,
    quality,
    scale,
    resolution,
    audio,
    batchMs,
    idleHeartbeatMs,
  };
  validateOpts(opts);

  const apiLevel = await this.adb.getApiLevel();
  if (apiLevel < MIN_API_LEVEL) {
    throw new errors.InvalidArgumentError(
      `Media streaming is not available on API Level ${apiLevel}. Minimum required API Level is ${MIN_API_LEVEL}.`,
    );
  }
  if (kind === 'video' && codec === 'hevc') {
    const encoders = await queryVideoEncoders(this.adb);
    if (encoders && !encoders.some((e) => e.codec === 'hevc')) {
      throw new errors.InvalidArgumentError('This device has no HEVC encoder, use codec=h264');
    }
  }
  const streams = (this._mediaStreams ??= new Map());
  for (const [id, s] of streams) {
    if (s.isStopped) {
      streams.delete(id);
    }
  }
  if ([...streams.values()].some((s) => s.kind === kind)) {
    throw new errors.InvalidArgumentError(`A ${kind} media stream is already running in this session`);
  }

  // No await between the check above and the registration below, or concurrent starts could both pass
  const session =
    kind === 'video' ? this.settingsApp.makeVideoStreamSession() : this.settingsApp.makeJpegStreamSession();
  const stream = new MediaStream(session, opts, {
    emit: (event) => {
      if (hasSubscribers(this, event.method)) {
        this.eventEmitter.emit(BIDI_EVENT_NAME, event);
      }
    },
    emitter: this.eventEmitter,
    hasSubscribers: () => hasSubscribers(this, MEDIA_UNITS_EVENT),
    log: this.log,
    // Ended streams stay registered so that a later stop call is a no-op, see mobileStopMediaStream
    onEnded: () => {},
  });
  streams.set(stream.streamId, stream);
  try {
    await this.settingsApp.adjustMediaProjectionServicePermissions();
    return await stream.start();
  } catch (e) {
    streams.delete(stream.streamId);
    await stream.stop('failed to start');
    throw e instanceof TypeError || e instanceof RangeError ? new errors.InvalidArgumentError(e.message) : e;
  }
}

/**
 * Reports whether media streaming works on the device, and which video codecs it can encode.
 * A software-only encoder may be limited to small frame sizes, the stream is scaled down to fit.
 */
export async function mobileGetMediaStreamCapabilities(this: AndroidDriver): Promise<MediaStreamCapabilities> {
  return await getCapabilities(this.adb);
}

/**
 * Stops a media stream started by `mobile: startMediaStream`.
 * Stopping a stream that has already ended is a no-op.
 *
 * @param streamId The id returned by `mobile: startMediaStream`.
 */
export async function mobileStopMediaStream(this: AndroidDriver, streamId: string): Promise<void> {
  const stream = this._mediaStreams?.get(streamId);
  if (!stream) {
    throw new errors.InvalidArgumentError(`Unknown media stream id '${streamId}'`);
  }
  await stream.stop();
}

/** Stops all streams of the session; used on session deletion. */
export async function stopAllMediaStreams(this: AndroidDriver): Promise<void> {
  await Promise.all([...(this._mediaStreams?.values() ?? [])].map((s) => s.stop('session ended')));
}

function hasSubscribers(driver: AndroidDriver, method: string): boolean {
  return [method, MEDIA_STREAM_MODULE].some((e) => (driver.bidiEventSubs[e]?.length ?? 0) > 0);
}

function validateOpts({
  kind,
  codec,
  bitrate,
  quality,
  scale,
  audio,
  batchMs,
  idleHeartbeatMs,
}: StartMediaStreamOpts & {batchMs: number; idleHeartbeatMs: number}): void {
  if (kind !== 'video' && kind !== 'jpeg') {
    throw new errors.InvalidArgumentError(`'kind' must be either 'video' or 'jpeg', got '${String(kind)}'`);
  }
  const wrongKind = kind === 'video' ? {quality, scale} : {codec, bitrate, audio};
  const offending = Object.entries(wrongKind).filter(([, v]) => v !== undefined);
  if (offending.length) {
    throw new errors.InvalidArgumentError(
      `${offending.map(([k]) => `'${k}'`).join(', ')} not supported for kind '${kind}'`,
    );
  }
  if (!Number.isInteger(batchMs) || batchMs < 0 || !Number.isInteger(idleHeartbeatMs) || idleHeartbeatMs < 0) {
    throw new errors.InvalidArgumentError(`'batchMs' and 'idleHeartbeatMs' must be non-negative integers`);
  }
}
