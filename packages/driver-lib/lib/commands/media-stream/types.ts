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
