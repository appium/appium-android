import assert from 'node:assert/strict';
import {EventEmitter} from 'node:events';
import {describe, it} from 'node:test';

import type {BiDiEvent} from '@appium/types';
import type {AccessUnit, JpegFrame} from 'io.appium.settings';

import {UnitBatcher} from '../../../lib/commands/media-stream/batcher.js';
import {parseVideoEncoders} from '../../../lib/commands/media-stream/capabilities.js';
import {readJpegSize} from '../../../lib/commands/media-stream/jpeg.js';
import {MediaStream} from '../../../lib/commands/media-stream/stream.js';
import type {MediaUnit} from '../../../lib/commands/media-stream/types.js';

const log = {debug() {}, warn() {}, info() {}} as any;

function unit(track: MediaUnit['track'], seq: number, key = false): MediaUnit {
  return {track, seq, ts: seq, key, data: Buffer.from([seq])};
}

function au(track: 'video' | 'audio', sequence: number, extra: Partial<AccessUnit> = {}): AccessUnit {
  return {
    track,
    data: Buffer.from([sequence]),
    sequence,
    timestampMicros: sequence,
    isKeyFrame: false,
    isConfig: false,
    ...extra,
  };
}

/** Fake settings session whose units are pushed by the test */
function fakeSession<T>() {
  const queue: T[] = [];
  let wake: (() => void) | undefined;
  let closed = false;
  const iter = async function* () {
    while (!closed || queue.length) {
      if (!queue.length) {
        await new Promise<void>((r) => (wake = r));
        continue;
      }
      yield queue.shift() as T;
    }
  };
  return {
    push(item: T) {
      queue.push(item);
      wake?.();
    },
    async start() {
      return true;
    },
    async stop() {
      closed = true;
      wake?.();
      return true;
    },
    keyFrameRequests: 0,
    async requestKeyFrame() {
      this.keyFrameRequests++;
      return true;
    },
    accessUnits: iter,
    frames: iter,
  };
}

function setup(kind: 'video' | 'jpeg', subscribed = true, extra: Record<string, unknown> = {}) {
  const session = fakeSession<any>();
  const events: BiDiEvent[] = [];
  const state = {subscribed};
  const emitter = new EventEmitter();
  const stream = new MediaStream(
    session as any,
    {kind, batchMs: 0, idleHeartbeatMs: 0, ...extra},
    {
      emit: (e) => events.push(e),
      emitter,
      hasSubscribers: () => state.subscribed,
      log,
      onEnded: () => {},
    },
  );
  const tick = () => new Promise((r) => setImmediate(r));
  // A video stream is only started once the encoder reported its config
  const begin = async () => {
    if (kind === 'video') {
      session.push(au('video', 0, {isConfig: true, data: Buffer.from('C0')}));
    }
    return await stream.start();
  };
  return {session, events, state, emitter, stream, tick, begin};
}

const unitEvents = (events: BiDiEvent[]) => events.filter((e) => e.method === 'appium:mediaStream.units');

describe('UnitBatcher', function () {
  it('cuts batches at video keyframes and track changes', function () {
    const batches: MediaUnit[][] = [];
    const b = new UnitBatcher(1000, (u) => batches.push(u));
    b.push(unit('video', 1, true));
    b.push(unit('video', 2));
    b.push(unit('video', 3, true));
    b.push(unit('audio', 1, true));
    b.push(unit('audio', 2, true));
    b.flush();
    assert.deepEqual(
      batches.map((x) => x.map((u) => `${u.track}${u.seq}`)),
      [['video1', 'video2'], ['video3'], ['audio1', 'audio2']],
    );
    b.close();
  });

  it('does not batch jpeg frames', function () {
    const batches: MediaUnit[][] = [];
    const b = new UnitBatcher(1000, (u) => batches.push(u));
    b.push(unit('jpeg', 1, true));
    b.push(unit('jpeg', 2, true));
    assert.equal(batches.length, 2);
  });
});

describe('readJpegSize', function () {
  it('reads dimensions from SOF0', function () {
    const jpeg = Buffer.from([
      0xff, 0xd8, 0xff, 0xc0, 0x00, 0x11, 0x08, 0x03, 0x20, 0x01, 0x90, 0x03, 0, 0, 0, 0, 0, 0, 0, 0,
    ]);
    assert.deepEqual(readJpegSize(jpeg), {width: 400, height: 800});
  });

  it('returns null for garbage', function () {
    assert.equal(readJpegSize(Buffer.from([0xff, 0xd8, 0x00, 0x00, 0, 0, 0, 0, 0, 0, 0, 0])), null);
  });
});

describe('MediaStream', function () {
  it('inlines config into keyframes and does not forward it', async function () {
    const {session, events, stream, tick, begin} = setup('video');
    await begin();
    session.push(au('video', 1, {isConfig: true, data: Buffer.from('CFG')}));
    session.push(au('video', 2, {isKeyFrame: true, data: Buffer.from('KEY')}));
    session.push(au('video', 3));
    await tick();
    const evs = unitEvents(events);
    assert.equal(evs.length, 2);
    assert.equal(Buffer.from(evs[0].params!.units[0].data, 'base64').toString(), 'CFGKEY');
    assert.deepEqual(evs[0].delivery, {policy: 'droppable', chain: `${stream.streamId}:video`, syncPoint: true});
    assert.equal(evs[1].delivery?.syncPoint, false);
    await stream.stop();
  });

  it('announces a format change on the second config', async function () {
    const {session, events, stream, tick, begin} = setup('video');
    await begin();
    session.push(au('video', 1, {isConfig: true}));
    await tick();
    assert.equal(events.filter((e) => e.method === 'appium:mediaStream.formatChanged').length, 1);
    await stream.stop();
  });

  it('waits for a keyframe after the subscriber appears', async function () {
    const {session, events, state, stream, tick, begin} = setup('video', false);
    await begin();
    session.push(au('video', 1, {isKeyFrame: true}));
    await tick();
    state.subscribed = true;
    session.push(au('video', 2));
    session.push(au('video', 3, {isKeyFrame: true}));
    await tick();
    assert.deepEqual(
      unitEvents(events).map((e) => e.params!.units[0].seq),
      [3],
    );
    await stream.stop();
  });

  it('marks audio reliable and not part of the video chain', async function () {
    const {session, events, stream, tick, begin} = setup('video', true, {audio: true});
    await begin();
    session.push(au('audio', 1));
    await tick();
    assert.deepEqual(unitEvents(events)[0].delivery, {policy: 'reliable'});
    assert.equal(unitEvents(events)[0].params!.units[0].key, true);
    await stream.stop();
  });

  it('emits one droppable jpeg per event and replays the last one when idle', async function () {
    const {session, events, stream, tick} = setup('jpeg', true, {idleHeartbeatMs: 20});
    await stream.start();
    const sof = Buffer.from([
      0xff, 0xd8, 0xff, 0xc0, 0x00, 0x11, 0x08, 0x00, 0x10, 0x00, 0x08, 0x03, 0, 0, 0, 0, 0, 0, 0, 0,
    ]);
    session.push({data: sof, sequence: 5, timestampMicros: 7} as JpegFrame);
    await tick();
    assert.deepEqual(unitEvents(events)[0].delivery, {policy: 'droppable'});
    assert.deepEqual(events.find((e) => e.method === 'appium:mediaStream.formatChanged')?.params, {
      streamId: stream.streamId,
      width: 8,
      height: 16,
    });
    await new Promise((r) => setTimeout(r, 80));
    const evs = unitEvents(events);
    assert.ok(evs.length >= 2, 'heartbeat re-emits the last frame');
    assert.deepEqual(
      evs.map((e) => e.params!.units[0].seq),
      evs.map((_, i) => i),
    );
    await stream.stop();
  });

  it('reports backpressure drops in the next units event and emits ended once', async function () {
    const {session, events, emitter, stream, tick} = setup('jpeg');
    await stream.start();
    emitter.emit('bidiBackpressure', {method: 'appium:mediaStream.units', state: 'dropping', dropped: 4});
    session.push({data: Buffer.from([1]), sequence: 0, timestampMicros: 0} as JpegFrame);
    await tick();
    assert.equal(unitEvents(events)[0].params!.dropped, 4);
    await stream.stop();
    await stream.stop();
    assert.equal(events.filter((e) => e.method === 'appium:mediaStream.ended').length, 1);
  });

  it('requests a keyframe when a subscriber appears and when core starts dropping, rate-limited', async function () {
    const {session, state, emitter, stream, tick, begin} = setup('video', false);
    await begin();
    session.push(au('video', 1, {isKeyFrame: true}));
    await tick();
    assert.equal(session.keyFrameRequests, 0);
    state.subscribed = true;
    session.push(au('video', 2));
    await tick();
    assert.equal(session.keyFrameRequests, 1);
    emitter.emit('bidiBackpressure', {method: 'appium:mediaStream.units', state: 'dropping', dropped: 1});
    assert.equal(session.keyFrameRequests, 1, 'rate-limited within the minimum interval');
    await stream.stop();
  });

  it('does not request keyframes for jpeg streams', async function () {
    const {session, emitter, stream} = setup('jpeg');
    await stream.start();
    emitter.emit('bidiBackpressure', {method: 'appium:mediaStream.units', state: 'dropping', dropped: 1});
    assert.equal(session.keyFrameRequests, 0);
    await stream.stop();
  });

  describe('lifecycle', function () {
    function build(session: Record<string, unknown>, extra: Record<string, unknown> = {}) {
      const events: BiDiEvent[] = [];
      const stream = new MediaStream(
        session as any,
        {kind: 'jpeg', batchMs: 0, idleHeartbeatMs: 0, ...extra},
        {emit: (e) => events.push(e), emitter: new EventEmitter(), hasSubscribers: () => true, log, onEnded: () => {}},
      );
      return {stream, events};
    }
    const withTimeout = <T>(p: Promise<T>) =>
      Promise.race([p, new Promise<never>((_, rej) => setTimeout(() => rej(new Error('timed out')), 500))]);

    it('leaves a stream of another client alone when the device is busy', async function () {
      let stops = 0;
      const {stream, events} = build({
        start: async () => false,
        stop: async () => {
          stops++;
          return true;
        },
      });
      await assert.rejects(stream.start(), /already running/);
      await stream.stop('failed to start');
      assert.equal(stops, 0);
      assert.deepEqual(events, []);
    });

    it('stops the device session if start() throws midway', async function () {
      let stops = 0;
      const {stream, events} = build({
        start: async () => {
          throw new Error('not running after 10000ms');
        },
        stop: async () => {
          stops++;
          return true;
        },
      });
      await assert.rejects(stream.start(), /not running/);
      await stream.stop('failed to start');
      assert.equal(stops, 1);
      assert.deepEqual(events, [], 'no ended event for a stream that never started');
    });

    it('finishes cleanly when the device ends the stream by itself', async function () {
      const {stream, events} = build({start: async () => true, stop: async () => true, async *frames() {}});
      await stream.start();
      await withTimeout((stream as any).pump);
      await withTimeout(stream.stop());
      assert.equal(stream.isStopped, true);
      assert.equal(events.filter((e) => e.method === 'appium:mediaStream.ended').length, 1);
    });

    it('does not emit units after the stream was stopped', async function () {
      let release!: () => void;
      const gate = new Promise<void>((r) => (release = r));
      const {stream, events} = build(
        {
          start: async () => true,
          stop: async () => true,
          async *accessUnits() {
            yield au('video', 0, {isConfig: true});
            await gate;
            yield au('audio', 1);
          },
        },
        {kind: 'video', batchMs: 10},
      );
      await stream.start();
      const stopping = stream.stop();
      release();
      await stopping;
      await new Promise((r) => setTimeout(r, 50));
      const endedAt = events.findIndex((e) => e.method === 'appium:mediaStream.ended');
      assert.ok(endedAt >= 0);
      assert.equal(events.slice(endedAt + 1).length, 0);
      assert.equal(unitEvents(events).length, 0);
    });
  });

  describe('first output of a video stream', function () {
    it('announces started only after the encoder reported its config', async function () {
      const {session, events, stream, tick} = setup('video');
      const started = stream.start();
      await tick();
      assert.equal(events.length, 0);
      session.push(au('video', 0, {isConfig: true}));
      await started;
      assert.deepEqual(
        events.map((e) => e.method),
        ['appium:mediaStream.started'],
      );
      await stream.stop();
    });

    it('fails and stops the device session if the stream ends without any output', async function () {
      let stops = 0;
      const events: BiDiEvent[] = [];
      const stream = new MediaStream(
        {
          start: async () => true,
          stop: async () => {
            stops++;
            return true;
          },
          async *accessUnits() {},
        } as any,
        {kind: 'video', batchMs: 0, idleHeartbeatMs: 0},
        {emit: (e) => events.push(e), emitter: new EventEmitter(), hasSubscribers: () => true, log, onEnded: () => {}},
      );
      await assert.rejects(stream.start(), /ended before producing any output.*device encoder may not support/s);
      await stream.stop('failed to start');
      assert.equal(stops, 1);
      assert.deepEqual(events, [], 'neither started nor ended is announced');
    });

    it('fails if no output arrives in time', async function (t) {
      t.mock.timers.enable({apis: ['setTimeout']});
      let stops = 0;
      let closeSource!: () => void;
      const sourceClosed = new Promise<void>((resolve) => (closeSource = resolve));
      const stream = new MediaStream(
        {
          start: async () => true,
          stop: async () => {
            stops++;
            closeSource();
            return true;
          },
          async *accessUnits() {
            yield await sourceClosed;
          },
        } as any,
        {kind: 'video', batchMs: 0, idleHeartbeatMs: 0},
        {emit() {}, emitter: new EventEmitter(), hasSubscribers: () => true, log, onEnded() {}},
      );
      const started = assert.rejects(stream.start(), /no output within \d+ms.*device encoder may not support/s);
      await new Promise((r) => setImmediate(r));
      t.mock.timers.tick(10_000);
      await started;
      await stream.stop('failed to start');
      assert.equal(stops, 1);
      t.mock.timers.reset();
    });

    it('does not wait for output on jpeg streams', async function () {
      const {events, stream} = setup('jpeg');
      await stream.start();
      assert.deepEqual(
        events.map((e) => e.method),
        ['appium:mediaStream.started'],
      );
      await stream.stop();
    });
  });
});
describe('parseVideoEncoders', function () {
  // Trimmed from `dumpsys media.player` of an API 36 emulator
  const dump = `
Decoder infos by media types:
=============================

Media type 'video/hevc':
  Decoder "c2.goldfish.hevc.decoder" supports
    attributes: 0xa: [
      encoder: 0,
      vendor: 1,
      software-only: 0,
      hw-accelerated: 1 ]
    details: AMessage(what = 0x00000000) = {
        string size-range = "64x64-4096x4096"
      }

Encoder infos by media types:
=============================

Media type 'audio/mp4a-latm':
  Encoder "c2.android.aac.encoder" supports
    attributes: 0x5: [
      encoder: 1,
      vendor: 0,
      software-only: 1,
      hw-accelerated: 0 ]

Media type 'video/avc':
  Encoder "c2.android.avc.encoder" supports
    attributes: 0x5: [
      encoder: 1,
      vendor: 0,
      software-only: 1,
      hw-accelerated: 0 ]
    details: AMessage(what = 0x00000000) = {
        string size-range = "16x16-2048x2048"
      }
Media type 'video/hevc':
  Encoder "c2.android.hevc.encoder" supports
    attributes: 0x5: [
      encoder: 1,
      vendor: 0,
      software-only: 1,
      hw-accelerated: 0 ]
    details: AMessage(what = 0x00000000) = {
        string size-range = "2x2-512x512"
      }
  Encoder "c2.vendor.hevc.encoder" supports
    attributes: 0xa: [
      encoder: 1,
      vendor: 1,
      software-only: 0,
      hw-accelerated: 1 ]
`;

  it('lists the video encoders with their limits and ignores decoders and audio', function () {
    assert.deepEqual(parseVideoEncoders(dump), [
      {name: 'c2.android.avc.encoder', codec: 'h264', softwareOnly: true, maxWidth: 2048, maxHeight: 2048},
      {name: 'c2.android.hevc.encoder', codec: 'hevc', softwareOnly: true, maxWidth: 512, maxHeight: 512},
      {name: 'c2.vendor.hevc.encoder', codec: 'hevc', softwareOnly: false},
    ]);
  });

  it('returns null if the device does not list its codecs', function () {
    assert.equal(parseVideoEncoders(''), null);
    assert.equal(parseVideoEncoders("Can't find service: media.player"), null);
  });
});
