const CFT_DOWNLOADS_URL =
  'https://googlechromelabs.github.io/chrome-for-testing/known-good-versions-with-downloads.json';
const LEGACY_STORAGE_URL = 'https://chromedriver.storage.googleapis.com';

const exeName = (platform) => (platform.startsWith('win') ? 'chromedriver.exe' : 'chromedriver');

/** Chrome for Testing builds (115+), the archives are checked against their `x-goog-hash` MD5 */
class ChromeForTestingSource {
  #entries;

  constructor(entries) {
    this.#entries = entries;
  }

  static async load() {
    const res = await fetch(CFT_DOWNLOADS_URL);
    if (!res.ok) {
      throw new Error(`Cannot retrieve ${CFT_DOWNLOADS_URL}: HTTP ${res.status}`);
    }
    return new ChromeForTestingSource((await res.json()).versions);
  }

  find(version) {
    return this.#entries.find((v) => v.version === version && v.downloads.chromedriver);
  }

  async fetchChecksums(entry, hasher) {
    const result = {};
    for (const {platform, url} of entry.downloads.chromedriver) {
      result[platform] = await hasher.sha256OfMember(url, `chromedriver-${platform}/${exeName(platform)}`);
    }
    return result;
  }
}

/** Legacy storage builds (up to 114), the archives are checked against their MD5 ETag */
class LegacyStorageSource {
  async fetchChecksums(version, hasher) {
    const res = await fetch(`${LEGACY_STORAGE_URL}/?prefix=${encodeURIComponent(version)}/`);
    if (!res.ok) {
      throw new Error(`Cannot list ${LEGACY_STORAGE_URL} for ${version}: HTTP ${res.status}`);
    }
    const xml = await res.text();
    const result = {};
    for (const [, content] of xml.matchAll(/<Contents>(.*?)<\/Contents>/gs)) {
      const key = /<Key>([^<]+)<\/Key>/.exec(content)?.[1] ?? '';
      const platform = /^[^/]+\/chromedriver_([a-z0-9_]+)\.zip$/.exec(key)?.[1];
      if (!platform) {
        continue;
      }
      const expectedMd5 = /<ETag>"?([0-9a-f]{32})"?<\/ETag>/.exec(content)?.[1];
      if (!expectedMd5) {
        // never generate a checksum for an archive that cannot be verified
        throw new Error(`The listing has no valid MD5 ETag for ${key}`);
      }
      result[platform] = await hasher.sha256OfMember(`${LEGACY_STORAGE_URL}/${key}`, exeName(platform), {expectedMd5});
    }
    return result;
  }
}

/** Chromedriver builds from Chrome for Testing, falling back to the legacy storage for older versions */
export class ChromedriverSource {
  #cft;
  #legacy;

  constructor(cft, legacy) {
    this.#cft = cft;
    this.#legacy = legacy;
  }

  static async load() {
    return new ChromedriverSource(await ChromeForTestingSource.load(), new LegacyStorageSource());
  }

  async fetchChecksums(version, hasher) {
    const entry = this.#cft.find(version);
    const result = entry
      ? await this.#cft.fetchChecksums(entry, hasher)
      : await this.#legacy.fetchChecksums(version, hasher);
    if (Object.keys(result).length === 0) {
      throw new Error(`No chromedriver downloads were found for version ${version}`);
    }
    return result;
  }
}
