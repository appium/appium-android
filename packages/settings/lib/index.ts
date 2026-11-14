import {getSettingsApkPath} from './utils/index.js';

export const path = getSettingsApkPath();
export * from './constants.js';
export {SettingsApp} from './client.js';
export type {AccessUnit, JpegFrame, StartJpegStreamOpts, StartVideoStreamOpts} from './commands/types.js';
export type {JpegStreamSession} from './commands/jpeg-stream.js';
export type {VideoStreamSession} from './commands/video-stream.js';
