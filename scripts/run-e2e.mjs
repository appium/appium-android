#!/usr/bin/env node
/* eslint-disable no-console */
// Runs a package's e2e suite (args are passed to `node`) only if an Android device/emulator is
// online at start; otherwise skips with exit 0. A device lost mid-run still fails the suite.
import {execFile, spawn} from 'node:child_process';
import path from 'node:path';
import {promisify} from 'node:util';

const execFileAsync = promisify(execFile);

async function hasOnlineDevice() {
  const sdkRoot = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT;
  const candidates = [sdkRoot && path.join(sdkRoot, 'platform-tools', 'adb'), 'adb'].filter(Boolean);
  for (const adb of candidates) {
    try {
      const {stdout} = await execFileAsync(adb, ['devices'], {timeout: 30_000});
      return stdout
        .split('\n')
        .slice(1)
        .some((line) => /\sdevice\s*$/.test(line));
    } catch {
      // try the next candidate
    }
  }
  return false;
}

if (!(await hasOnlineDevice())) {
  console.log('No online Android device/emulator found; skipping e2e tests.');
  process.exit(0);
}

const child = spawn(process.execPath, process.argv.slice(2), {stdio: 'inherit'});
child.on('error', (err) => {
  console.error(err);
  process.exit(1);
});
child.on('close', (code, signal) => process.exit(code ?? (signal ? 1 : 0)));
