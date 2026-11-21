import path from 'node:path';
import {parseArgs} from 'node:util';

import {ChecksumsStore} from './lib/checksums-store.mjs';
import {ChecksumsUpdater} from './lib/checksums-updater.mjs';
import {ChromedriverSource} from './lib/chromedriver-source.mjs';
import {VersionsMapping} from './lib/versions-mapping.mjs';

const DEFAULT_BACKFILL_COUNT = 0;

async function main() {
  const {
    positionals: [latestVersion, mappingPath],
    values,
  } = parseArgs({
    allowPositionals: true,
    options: {
      checksums: {type: 'string'},
      backfill: {type: 'string', default: String(DEFAULT_BACKFILL_COUNT)},
    },
  });
  if (!latestVersion) {
    throw new Error('The latest Chromedriver version must be provided as the first command line argument');
  }
  if (!mappingPath) {
    throw new Error('The path to the versions mapping json must be provided as the second command line argument');
  }
  const checksumsPath = values.checksums ?? path.join(path.dirname(mappingPath), 'checksums.json');
  const backfill = parseInt(values.backfill, 10);

  const mapping = await VersionsMapping.load(mappingPath);
  // The mapping is only written after the checksums of the latest version are known
  const isNewVersion = mapping.addLatest(latestVersion);
  const store = await ChecksumsStore.load(checksumsPath, mapping.compare);

  // the latest version plus `--backfill N` most recent older ones that still have no checksums
  const missing = mapping.versions.filter((v) => !store.has(v));
  const older = missing.filter((v) => v !== latestVersion).slice(0, backfill);
  const targets = missing.includes(latestVersion) ? [latestVersion, ...older] : older;

  let computed = [];
  if (targets.length > 0) {
    const updater = new ChecksumsUpdater({store, source: await ChromedriverSource.load()});
    ({computed} = await updater.update(targets, {required: latestVersion}));
  }
  if (isNewVersion) {
    await mapping.save();
  }
  process.stdout.write(isNewVersion || computed.length > 0 ? '1' : '0');
}

await main();
