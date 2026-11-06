import assert from 'node:assert/strict';
import {describe, it} from 'node:test';

import {errors} from 'appium/driver.js';

import {AndroidDriver} from '../../../lib/driver.js';

describe('findElOrEls', function () {
  for (const mult of [false, true]) {
    it(`rejects an empty selector as an invalid selector when multiple is ${mult}`, async function () {
      const driver = new AndroidDriver();
      await assert.rejects(driver.findElOrEls('id', '', mult as any), errors.InvalidSelectorError);
    });
  }
});
