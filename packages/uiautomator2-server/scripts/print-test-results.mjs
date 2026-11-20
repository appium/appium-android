import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const DEFAULT_RESULTS_DIR = path.resolve(
  __dirname, '..', 'app', 'build', 'outputs', 'androidTest-results', 'connected',
);
const MAX_TRACE_LINES = 15;

const unescapeXml = (s) => s
  .replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"')
  .replace(/&apos;/g, "'").replace(/&amp;/g, '&');

async function* findXmlReports(dir) {
  for (const entry of await fs.readdir(dir, {withFileTypes: true}).catch(() => [])) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      yield* findXmlReports(full);
    } else if (/^TEST-.*\.xml$/.test(entry.name)) {
      yield full;
    }
  }
}

/** Prints one line per test (PASS/FAIL/SKIP) plus the failure details */
async function printResults(resultsDir) {
  for await (const file of findXmlReports(resultsDir)) {
    const xml = await fs.readFile(file, 'utf8');
    const cases = xml.matchAll(
      /<testcase\s([^>]*?)(?:\/>|>([\s\S]*?)<\/testcase>)/g,
    );
    for (const [, attrs, body = ''] of cases) {
      const attr = (name) => unescapeXml(attrs.match(new RegExp(`\\b${name}="([^"]*)"`))?.[1] ?? '');
      const test = `${attr('classname').split('.').pop()}.${attr('name')}`;
      const time = `${Number(attr('time')).toFixed(1)}s`;
      const problem = body.match(/<(failure|error)\b[^>]*>([\s\S]*?)<\/\1>/);
      if (problem) {
        console.log(`FAIL  ${test} (${time})`);
        const trace = unescapeXml(problem[2]).trim().split('\n').slice(0, MAX_TRACE_LINES);
        console.log(trace.map((line) => `      ${line}`).join('\n'));
      } else if (/<skipped\b/.test(body)) {
        console.log(`SKIP  ${test}`);
      } else {
        console.log(`PASS  ${test} (${time})`);
      }
    }
  }
}

await printResults(process.argv[2] ? path.resolve(process.argv[2]) : DEFAULT_RESULTS_DIR);
