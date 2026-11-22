import {readJson, writeJson} from './json-file.mjs';

/** A checksums json file: `{version: {platform: sha256}}`, written sorted with `compare` */
export class ChecksumsStore {
  #filePath;
  #data;
  #compare;
  #dirty = false;

  constructor(filePath, data, compare) {
    this.#filePath = filePath;
    this.#data = data;
    this.#compare = compare;
  }

  /**
   * @param {string} filePath - Missing files are treated as empty
   * @param {(a: string, b: string) => number} compare - The order of the versions in the file
   */
  static async load(filePath, compare) {
    return new ChecksumsStore(filePath, await readJson(filePath, {}), compare);
  }

  has(version) {
    return version in this.#data;
  }

  set(version, checksums) {
    this.#data[version] = checksums;
    this.#dirty = true;
  }

  /** Writes the file only if something has changed */
  async save() {
    if (!this.#dirty) {
      return;
    }
    const versions = Object.keys(this.#data).sort(this.#compare);
    await writeJson(this.#filePath, Object.fromEntries(versions.map((v) => [v, this.#data[v]])));
    this.#dirty = false;
  }
}
