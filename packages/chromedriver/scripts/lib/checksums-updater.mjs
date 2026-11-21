import {asyncmap} from 'asyncbox';

import {ArchiveHasher} from './archive-hasher.mjs';

const DEFAULT_CONCURRENCY = 4;

/**
 * Computes the checksums of the given versions in batches and stores them.
 * A source is an object with `fetchChecksums(version, hasher)` returning `{platform: sha256}`.
 */
export class ChecksumsUpdater {
  #store;
  #source;
  #concurrency;
  #log;

  constructor({store, source, concurrency = DEFAULT_CONCURRENCY, log = (msg) => process.stderr.write(`${msg}\n`)}) {
    this.#store = store;
    this.#source = source;
    this.#concurrency = concurrency;
    this.#log = log;
  }

  /**
   * @param {string[]} versions
   * @param {{required?: string}} [opts] - A failure for the `required` version aborts the run,
   * failures for the other ones are only reported
   * @returns {Promise<{computed: string[], failed: string[]}>}
   */
  async update(versions, {required} = {}) {
    const computed = [];
    const failed = [];
    try {
      await ArchiveHasher.withTempDir((hasher) =>
        asyncmap(
          versions,
          async (version) => {
            try {
              this.#store.set(version, await this.#source.fetchChecksums(version, hasher));
              computed.push(version);
              this.#log(`Computed checksums for ${version}`);
            } catch (e) {
              if (version === required) {
                throw e;
              }
              failed.push(version);
              this.#log(`Skipping ${version}: ${e.message}`);
            }
          },
          {concurrency: this.#concurrency},
        ),
      );
    } finally {
      // keep the already computed checksums even if the run is aborted
      await this.#store.save();
    }
    if (failed.length > 0) {
      this.#log(`Versions without checksums: ${failed.join(', ')}`);
    }
    return {computed, failed};
  }
}
