// Removes build output and tsbuildinfo files from all packages/*
import {glob, rm} from 'node:fs/promises';

const PATTERNS = ['packages/*/build', 'packages/*/**/.tsconfig.tsbuildinfo'];

const targets = [];
for await (const entry of glob(PATTERNS, {exclude: (name) => name === 'node_modules'})) {
  targets.push(entry);
}
await Promise.all(targets.map((p) => rm(p, {recursive: true, force: true})));
