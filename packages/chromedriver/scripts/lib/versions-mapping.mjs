import {readJson, writeJson} from './json-file.mjs';

/** The `{chromedriver version: min chrome version}` mapping, the newest version goes first */
export class VersionsMapping {
  #filePath;
  #data;

  constructor(filePath, data) {
    this.#filePath = filePath;
    this.#data = data;
  }

  static async load(filePath) {
    return new VersionsMapping(filePath, await readJson(filePath));
  }

  get versions() {
    return Object.keys(this.#data);
  }

  /**
   * Adds the version in memory, call {@linkcode save} to persist it
   * @returns {boolean} `true` if the version was not in the mapping
   */
  addLatest(version) {
    if (version in this.#data) {
      return false;
    }
    this.#data = Object.fromEntries([[version, version], ...Object.entries(this.#data)]);
    return true;
  }

  /** Orders versions the same way as the mapping does */
  compare = (a, b) => this.versions.indexOf(a) - this.versions.indexOf(b);

  async save() {
    await writeJson(this.#filePath, this.#data);
  }
}
