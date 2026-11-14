import type {ADB} from 'appium-adb';
import {SETTINGS_HELPER_ID} from 'io.appium.settings';

import {MIN_API_LEVEL} from './constants.js';
import type {MediaStreamCapabilities, VideoEncoderInfo} from './types.js';

const VIDEO_MIMES = {'video/avc': 'h264', 'video/hevc': 'hevc'} as const;

/**
 * Extracts the video encoders from the `dumpsys media.player` codec listing
 * (Android 13+). Returns null if the listing has no encoder section at all.
 */
export function parseVideoEncoders(dump: string): VideoEncoderInfo[] | null {
  const encoders: VideoEncoderInfo[] = [];
  let mime: string | undefined;
  let current: VideoEncoderInfo | undefined;
  let sawEncoder = false;
  for (const line of dump.split('\n')) {
    const mimeMatch = /^Media type '([^']+)':/.exec(line);
    if (mimeMatch) {
      mime = mimeMatch[1];
      current = undefined;
      continue;
    }
    const encoderMatch = /^\s+Encoder "([^"]+)" supports/.exec(line);
    if (encoderMatch) {
      sawEncoder = true;
      const codec = mime && VIDEO_MIMES[mime as keyof typeof VIDEO_MIMES];
      current = codec ? {name: encoderMatch[1], codec, softwareOnly: false} : undefined;
      if (current) {
        encoders.push(current);
      }
      continue;
    }
    if (/^\s+Decoder "/.test(line)) {
      current = undefined;
      continue;
    }
    if (!current) {
      continue;
    }
    const software = /software-only: (\d)/.exec(line);
    if (software) {
      current.softwareOnly = software[1] === '1';
    }
    const size = /size-range = "(\d+)x(\d+)-(\d+)x(\d+)"/.exec(line);
    if (size) {
      current.maxWidth = Number(size[3]);
      current.maxHeight = Number(size[4]);
    }
  }
  return sawEncoder ? encoders : null;
}

/** Returns null if the device does not list its codecs. */
export async function queryVideoEncoders(adb: ADB): Promise<VideoEncoderInfo[] | null> {
  try {
    return parseVideoEncoders(await adb.shell(['dumpsys', 'media.player']));
  } catch {
    return null;
  }
}

export async function getCapabilities(adb: ADB): Promise<MediaStreamCapabilities> {
  const apiLevel = await adb.getApiLevel();
  if (apiLevel < MIN_API_LEVEL) {
    return {
      supported: false,
      reason: {
        code: 'api_level_too_low',
        message: `Media streaming requires API Level ${MIN_API_LEVEL}+, the device has ${apiLevel}`,
      },
      apiLevel,
      kinds: [],
    };
  }
  if (!(await adb.isAppInstalled(SETTINGS_HELPER_ID))) {
    return {
      supported: false,
      reason: {code: 'settings_app_missing', message: `${SETTINGS_HELPER_ID} is not installed on the device`},
      apiLevel,
      kinds: [],
    };
  }
  const encoders = await queryVideoEncoders(adb);
  return {
    supported: true,
    apiLevel,
    kinds: ['video', 'jpeg'],
    audio: true,
    // Without a codec listing (older Android) the codecs are assumed rather than verified
    codecs: encoders
      ? {
          h264: encoders.filter((e) => e.codec === 'h264'),
          hevc: encoders.filter((e) => e.codec === 'hevc'),
        }
      : null,
  };
}
