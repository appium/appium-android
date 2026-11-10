import assert from 'node:assert/strict';
import {describe, it, mock} from 'node:test';

import {toW3cCapNames, getCapValue, startSession} from '../../lib/commands/session.js';

describe('Protocol Helpers', function () {
  const caps = {
    loggingPrefs: {
      detach: true,
    },
    'goog:perfLoggingPrefs': {
      enableNetwork: true,
    },
    'some:chromeOptions': {
      detach: true,
    },
  };

  it('should properly add w3c prefixes where needed', function () {
    const result = toW3cCapNames(caps);
    assert.deepStrictEqual(result, {
      'goog:loggingPrefs': {
        detach: true,
      },
      'goog:perfLoggingPrefs': {
        enableNetwork: true,
      },
      'some:chromeOptions': {
        detach: true,
      },
    });
  });

  it('should properly parse values from different caps', function () {
    const v1 = getCapValue(caps, 'loggingPrefs');
    assert.deepStrictEqual(v1, {
      detach: true,
    });

    const v2 = getCapValue(caps, 'goog:perfLoggingPrefs');
    assert.deepStrictEqual(v2, {
      enableNetwork: true,
    });

    const v3 = getCapValue(
      {
        proxy: 'some',
      },
      'proxy',
    );
    assert.strictEqual(v3, 'some');

    const v4 = getCapValue(
      {
        proxy: 'some',
      },
      'goog:proxy',
      {},
    );
    assert.deepStrictEqual(v4, {});
  });

  describe('startSession', function () {
    function makeContext(driverVersion: string | null, capabilities: Record<string, any>) {
      const command = mock.fn(async () => ({capabilities: {}}));
      const ctx: any = {
        driverVersion,
        capabilities,
        _onlineStatus: null,
        jwproxy: {command, sessionId: null},
        log: {info() {}, warn() {}, debug() {}},
        emit() {},
      };
      return {ctx, command};
    }

    it('should force w3c for chromeOptions.w3c = false', async function () {
      const {ctx, command} = makeContext('120.0.0.0', {chromeOptions: {w3c: false}});
      await startSession.call(ctx);
      const [, , body] = command.mock.calls[0].arguments as any[];
      assert.strictEqual(body.capabilities.alwaysMatch['goog:chromeOptions'].w3c, true);
    });

    it('should reject Chromedriver versions without W3C support', async function () {
      const {ctx, command} = makeContext('74.0.3729.6', {});
      await assert.rejects(() => startSession.call(ctx), /does not support the W3C/);
      assert.strictEqual(command.mock.callCount(), 0);
    });
  });
});
