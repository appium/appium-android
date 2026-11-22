import {readFile, writeFile} from 'node:fs/promises';

/**
 * @param {string} filePath
 * @param {object} [fallback] - Returned if the file does not exist
 */
export async function readJson(filePath, fallback) {
  try {
    return JSON.parse(await readFile(filePath, 'utf8'));
  } catch (e) {
    if (e.code === 'ENOENT' && fallback !== undefined) {
      return fallback;
    }
    throw e;
  }
}

export async function writeJson(filePath, obj) {
  await writeFile(filePath, `${JSON.stringify(obj, null, 2)}\n`, 'utf8');
}
