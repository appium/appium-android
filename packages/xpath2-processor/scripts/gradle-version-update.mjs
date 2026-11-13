import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

import semver from 'semver';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const VERSION_PATTERN = /^xpath2Version=.+$/m;

function parseArgValue(argName) {
  const argNamePattern = new RegExp(`^--${argName}\\b`);
  for (let i = 1; i < process.argv.length; ++i) {
    const arg = process.argv[i];
    if (argNamePattern.test(arg)) {
      return arg.includes('=') ? arg.split('=')[1] : process.argv[i + 1];
    }
  }
  return null;
}

async function gradleVersionUpdate() {
  const gradleFile = path.resolve(__dirname, '..', 'gradle.properties');
  try {
    await fs.promises.access(gradleFile, fs.constants.W_OK);
  } catch {
    throw new Error(`No '${gradleFile}' file found or it is not writeable`);
  }

  const version = parseArgValue('package-version');
  if (!version) {
    throw new Error('No package version argument (use `--package-version=xxx`)');
  }
  if (!semver.valid(version)) {
    throw new Error(`Invalid version specified '${version}'. Version should be in the form '1.2.3'`);
  }

  const payload = await fs.promises.readFile(gradleFile, 'utf8');
  if (!VERSION_PATTERN.test(payload)) {
    throw new Error(`Cannot find the xpath2Version property in '${gradleFile}'`);
  }
  // eslint-disable-next-line no-console
  console.log(`Updating '${gradleFile}' to xpath2Version '${version}'`);
  await fs.promises.writeFile(gradleFile, payload.replace(VERSION_PATTERN, `xpath2Version=${version}`), 'utf8');
}

await gradleVersionUpdate();
