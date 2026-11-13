import type {BiDiEvent} from '@appium/types';

export type MediaKind = 'video' | 'jpeg';
export type MediaTrack = 'video' | 'audio' | 'jpeg';

/** A contract unit; video config is already inlined into keyframes. */
export interface MediaUnit {
  track: MediaTrack;
  seq: number;
  ts: number;
  key: boolean;
  data: Buffer;
}

/** The JSON shape of a unit inside `appium:mediaStream.units` */
export interface WireUnit extends Omit<MediaUnit, 'data'> {
  data: string;
}

export interface StartMediaStreamOpts {
  kind: MediaKind;
  codec?: 'h264' | 'hevc';
  fps?: number;
  bitrate?: number;
  quality?: number;
  scale?: number;
  resolution?: string;
  audio?: boolean;
  /** Batching window for video/audio units; JPEG is never batched */
  batchMs?: number;
  /** Re-emit the last JPEG after this much idle time (0 disables) */
  idleHeartbeatMs?: number;
}

export interface MediaStreamInfo {
  streamId: string;
  kind: MediaKind;
  tracks: MediaTrack[];
  codec: 'h264' | 'hevc' | 'jpeg';
  event: string;
  width?: number;
  height?: number;
  audio?: {sampleRate: number; channels: number};
}

export type MediaBiDiEvent = BiDiEvent;

export interface VideoEncoderInfo {
  name: string;
  codec: 'h264' | 'hevc';
  softwareOnly: boolean;
  /** Largest frame size the encoder accepts; the stream is scaled down to fit it */
  maxWidth?: number;
  maxHeight?: number;
}

export interface MediaStreamCapabilities {
  supported: boolean;
  reason?: {code: 'api_level_too_low' | 'settings_app_missing'; message: string};
  apiLevel: number;
  kinds: MediaKind[];
  audio?: boolean;
  /** Video encoders per codec; null if the device does not list its codecs */
  codecs?: {h264: VideoEncoderInfo[]; hevc: VideoEncoderInfo[]} | null;
}
