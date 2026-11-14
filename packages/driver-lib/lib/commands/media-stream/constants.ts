export const MEDIA_STREAM_MODULE = 'appium:mediaStream';
export const MEDIA_UNITS_EVENT = `${MEDIA_STREAM_MODULE}.units`;
export const MEDIA_STARTED_EVENT = `${MEDIA_STREAM_MODULE}.started`;
export const MEDIA_FORMAT_CHANGED_EVENT = `${MEDIA_STREAM_MODULE}.formatChanged`;
export const MEDIA_ENDED_EVENT = `${MEDIA_STREAM_MODULE}.ended`;
export const BACKPRESSURE_EVENT_NAME = 'bidiBackpressure';
export const MIN_API_LEVEL = 29;
export const KEYFRAME_REQUEST_MIN_INTERVAL_MS = 1000;
// The video encoder reports its config right after it starts, regardless of screen activity
export const FIRST_UNIT_TIMEOUT_MS = 5000;
export const DEFAULT_BATCH_MS = 40;
export const DEFAULT_IDLE_HEARTBEAT_MS = 1000;
// settings app defaults, reported in `started` (ADTS AAC-LC)
export const AUDIO_SAMPLE_RATE = 44100;
export const AUDIO_CHANNELS = 1;
