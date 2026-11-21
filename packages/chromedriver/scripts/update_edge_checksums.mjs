import {parseArgs} from 'node:util';

import {ChecksumsStore} from './lib/checksums-store.mjs';
import {ChecksumsUpdater} from './lib/checksums-updater.mjs';
import {EdgeSource} from './lib/edge-source.mjs';
import {compareVersions} from './lib/versions.mjs';

// the oldest major that is still available in the storage
const MIN_MAJOR = 118;

const major = (version) => parseInt(version, 10);

async function main() {
  const {
    positionals: [checksumsPath],
    values: {backfill},
  } = parseArgs({
    allowPositionals: true,
    options: {backfill: {type: 'boolean', default: false}},
  });
  if (!checksumsPath) {
    throw new Error('The path to the Edge WebDriver checksums json must be provided as the first argument');
  }

  const source = new EdgeSource();
  const stable = await source.getLatest('LATEST_STABLE');
  if (!stable) {
    throw new Error('Cannot determine the latest stable Edge WebDriver version');
  }
  // by default only the majors that are currently supported, `--backfill` goes through all available ones
  const extended = await source.getLatest('LATEST_EXTENDEDSTABLE');
  const majors = backfill
    ? Array.from({length: major(stable) - MIN_MAJOR + 1}, (_, i) => MIN_MAJOR + i)
    : [...new Set([major(stable), extended && major(extended)].filter(Boolean))];

  // newest first
  const store = await ChecksumsStore.load(checksumsPath, (a, b) => compareVersions(b, a));
  const targets = (await source.getReleaseVersions(majors)).filter((v) => !store.has(v));
  if (targets.length === 0) {
    process.stdout.write('0');
    return;
  }
  await new ChecksumsUpdater({store, source}).update(targets, {required: stable});
  process.stdout.write('1');
}

await main();
