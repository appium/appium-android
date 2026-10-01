#!/usr/bin/env node
/* eslint-disable no-console */
// Selects which packages/* need unit tests for the current diff: a package that changed itself,
// OR whose direct in-monorepo dependency changed, OR every package when a shared root
// config/tooling file changed. Falls back to running everything when the diff can't be computed.
import {execFile} from 'node:child_process';
import {readFile, readdir, appendFile, access} from 'node:fs/promises';
import {join} from 'node:path';
import {promisify} from 'node:util';

const execFileAsync = promisify(execFile);

const PACKAGES_DIR = join(process.cwd(), 'packages');
const baseSha = process.env.BASE_SHA;
const headSha = process.env.HEAD_SHA || 'HEAD';
const githubOutput = process.env.GITHUB_OUTPUT;

async function pathExists(path) {
  try {
    await access(path);
    return true;
  } catch {
    return false;
  }
}

function directWorkspaceDeps(pkg, nameToDir) {
  const deps = {...pkg.dependencies, ...pkg.devDependencies};
  return Object.keys(deps)
    .map((depName) => nameToDir.get(depName))
    .filter(Boolean);
}

// A dir is selected if it changed itself, or if any of its direct in-monorepo dependencies did.
function selectSelfOrDependency(dirs, changedDirs, pkgByDir, nameToDir) {
  return dirs.filter((dir) => {
    if (changedDirs.has(dir)) {
      return true;
    }
    return directWorkspaceDeps(pkgByDir.get(dir), nameToDir).some((depDir) => changedDirs.has(depDir));
  });
}

async function main() {
  const entries = await readdir(PACKAGES_DIR, {withFileTypes: true});
  const candidateDirs = entries.filter((entry) => entry.isDirectory()).map((entry) => entry.name);
  const unitDirs = (
    await Promise.all(
      candidateDirs.map(async (dir) => ((await pathExists(join(PACKAGES_DIR, dir, 'package.json'))) ? dir : null)),
    )
  ).filter(Boolean);

  const pkgByDir = new Map();
  const nameToDir = new Map();
  await Promise.all(
    unitDirs.map(async (dir) => {
      const pkg = JSON.parse(await readFile(join(PACKAGES_DIR, dir, 'package.json'), 'utf8'));
      pkgByDir.set(dir, pkg);
      nameToDir.set(pkg.name, dir);
    }),
  );

  let selected;
  if (!baseSha || /^0+$/.test(baseSha)) {
    console.log('Running tests for all packages: no usable base commit to diff against');
    selected = unitDirs;
  } else {
    let changedFiles;
    try {
      const {stdout} = await execFileAsync('git', ['diff', '--name-only', `${baseSha}...${headSha}`]);
      changedFiles = stdout.split('\n').filter(Boolean);
    } catch (err) {
      console.log(`git diff failed: ${err.message}`);
      changedFiles = null;
    }

    if (changedFiles === null) {
      console.log('Running tests for all packages: failed to compute the diff');
      selected = unitDirs;
    } else {
      const changedDirs = new Set(changedFiles.map((file) => file.match(/^packages\/([^/]+)\//)?.[1]).filter(Boolean));
      const sharedRootChanged = changedFiles.some((file) => !file.startsWith('packages/'));
      console.log(`Changed packages: ${[...changedDirs].join(', ') || '(none)'}`);
      if (sharedRootChanged) {
        console.log('Shared root config/tooling also changed - widening unit test selection to all packages.');
      }
      selected = sharedRootChanged ? unitDirs : selectSelfOrDependency(unitDirs, changedDirs, pkgByDir, nameToDir);
    }
  }

  console.log('Packages selected for unit tests:');
  for (const dir of selected) {
    console.log(`  - ${dir}`);
  }

  // lerna --scope needs npm package names, not directory names.
  const unitPackageNamesJson = JSON.stringify(selected.map((dir) => pkgByDir.get(dir).name));
  if (githubOutput) {
    await appendFile(githubOutput, `unit_packages=${unitPackageNamesJson}\n`);
  } else {
    console.log(unitPackageNamesJson);
  }
}

await main();
