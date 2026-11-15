// Setup: npm install ws tsx (mp4 mode also needs ffmpeg)
// Usage: npx tsx media-stream-client.mts mjpeg [port]  |  mp4 [file] [--audio]
import {spawn} from 'node:child_process';
import {createServer, type ServerResponse} from 'node:http';
import type {Writable} from 'node:stream';

import WebSocket from 'ws';

const APPIUM_URL = process.env.APPIUM_URL ?? 'http://127.0.0.1:4723';
const MEDIA_STREAM_MODULE = 'appium:mediaStream';

type Track = 'video' | 'audio' | 'jpeg';

interface Options {
  mode: 'mjpeg' | 'mp4';
  port: number;
  file: string;
  audio: boolean;
}

/** Where the decoded units of the stream go */
interface Output {
  onUnit(track: Track, data: Buffer, key: boolean): void;
  /** Resolves if the output stops working on its own */
  readonly failure?: Promise<void>;
  /** Finishes the output, and rejects if it has failed */
  close(): Promise<void>;
}

function parseArgs(argv: string[]): Options {
  const audio = argv.includes('--audio');
  const [mode, arg] = argv.filter((a) => !a.startsWith('--'));
  if (mode !== 'mjpeg' && mode !== 'mp4') {
    throw new Error('Usage: media-stream-client.mts mjpeg [port] | mp4 [file] [--audio]');
  }
  return {
    mode,
    port: mode === 'mjpeg' && arg ? Number(arg) : 8093,
    file: mode === 'mp4' && arg ? arg : 'screen.mp4',
    audio,
  };
}

/** Sends a WebDriver command to Appium and returns its value */
async function wd(method: string, path: string, body?: object): Promise<any> {
  const res = await fetch(APPIUM_URL + path, {
    method,
    headers: {'content-type': 'application/json'},
    body: body && JSON.stringify(body),
  });
  const {value} = (await res.json()) as {value: any};
  if (value?.error) {
    throw new Error(`${value.error}: ${value.message}`);
  }
  return value;
}

/** Serves the JPEG units as MJPEG (multipart/x-mixed-replace over HTTP) */
function createMjpegServer(port: number): Output {
  const viewers = new Set<ServerResponse>();
  let lastFrame: Buffer | undefined;

  const toPart = (jpeg: Buffer) =>
    Buffer.concat([
      Buffer.from(`--frame\r\nContent-Type: image/jpeg\r\nContent-Length: ${jpeg.length}\r\n\r\n`),
      jpeg,
      Buffer.from('\r\n'),
    ]);

  const server = createServer((req, res) => {
    res.writeHead(200, {
      'Content-Type': 'multipart/x-mixed-replace; boundary=frame',
      'Cache-Control': 'no-cache',
      Connection: 'close',
    });
    viewers.add(res);
    req.on('close', () => viewers.delete(res));
    if (lastFrame) {
      // A new viewer gets a picture right away
      res.write(toPart(lastFrame));
    }
  });
  server.listen(port, '127.0.0.1');
  console.log(`MJPEG: http://127.0.0.1:${port}`);

  return {
    onUnit(track, data) {
      if (track !== 'jpeg') {
        return;
      }
      lastFrame = data;
      const part = toPart(data);
      for (const res of viewers) {
        // Skip frames for a slow viewer instead of buffering them
        if (!res.writableNeedDrain) {
          res.write(part);
        }
      }
    },
    async close() {
      viewers.forEach((res) => res.end());
      server.close();
    },
  };
}

/** Remuxes the video (and audio) units into an .mp4 file with ffmpeg, without re-encoding */
function createMp4Recorder(file: string, codec: 'h264' | 'hevc', audio: boolean): Output {
  const ffmpegArgs = [
    '-y',
    // The raw stream has no timestamps, so use the arrival time of the units
    ...['-use_wallclock_as_timestamps', '1', '-f', codec, '-i', 'pipe:0'],
    ...(audio ? ['-f', 'aac', '-i', 'pipe:3'] : []),
    ...['-c', 'copy'],
    // The audio arrives as ADTS, mp4 needs the plain AAC frames
    ...(audio ? ['-bsf:a', 'aac_adtstoasc'] : []),
    // Keeps the file playable even if ffmpeg is killed before it finishes the file
    ...['-movflags', '+frag_keyframe+empty_moov'],
    file,
  ];
  const ffmpeg = spawn('ffmpeg', ffmpegArgs, {
    // Own process group: Ctrl+C must not stop ffmpeg before it has finished the file
    detached: true,
    windowsHide: true,
    stdio: ['pipe', 'inherit', 'inherit', ...(audio ? ['pipe' as const] : [])],
  });
  let spawnError: Error | undefined;
  ffmpeg.once('error', (e) => (spawnError = e));
  const exited = new Promise<number | null>((resolve) => ffmpeg.once('close', resolve));
  let closing = false;
  // Resolves if ffmpeg exits before close() was called
  const failure = new Promise<void>((resolve) => void exited.then(() => closing || resolve()));
  const inputs = {
    video: ffmpeg.stdin as Writable,
    audio: audio ? (ffmpeg.stdio[3] as Writable) : null,
  };
  // A broken pipe means that ffmpeg has exited, which is reported through its exit code
  Object.values(inputs).forEach((input) => input?.on('error', () => {}));
  console.log(`Recording to ${file}`);

  // An ffmpeg input that is full skips units instead of buffering them in memory, and pausing
  // the connection could starve the other input. Video resumes at the next keyframe.
  let videoSynced = false;
  return {
    failure,
    onUnit(track, data, key) {
      if (track === 'video') {
        if (inputs.video.writableNeedDrain) {
          videoSynced = false;
          return;
        }
        videoSynced ||= key;
        if (videoSynced) {
          inputs.video.write(data);
        }
      } else if (track === 'audio' && inputs.audio && !inputs.audio.writableNeedDrain) {
        inputs.audio.write(data);
      }
    },
    async close() {
      closing = true;
      inputs.video.end();
      inputs.audio?.end();
      const code = await exited;
      if (spawnError) {
        throw new Error(`Cannot run ffmpeg: ${spawnError.message}`);
      }
      if (code !== 0) {
        throw new Error(`ffmpeg failed with exit code ${code}`);
      }
    },
  };
}

/** Subscribes to the events and resolves once Appium has confirmed it */
function subscribe(ws: WebSocket, events: string[]): Promise<void> {
  const id = 1;
  return new Promise((resolve, reject) => {
    const onMessage = (raw: WebSocket.RawData) => {
      const msg = JSON.parse(raw.toString());
      if (msg.id !== id) {
        return;
      }
      ws.off('message', onMessage);
      if (msg.type === 'error') {
        reject(new Error(`${msg.error}: ${msg.message}`));
      } else {
        resolve();
      }
    };
    ws.on('message', onMessage);
    ws.send(JSON.stringify({id, method: 'session.subscribe', params: {events}}));
  });
}

async function main(): Promise<void> {
  const options = parseArgs(process.argv.slice(2));
  const codec = 'h264';
  const output =
    options.mode === 'mjpeg' ? createMjpegServer(options.port) : createMp4Recorder(options.file, codec, options.audio);

  const {sessionId, capabilities} = await wd('POST', '/session', {
    capabilities: {
      alwaysMatch: {
        platformName: 'Android',
        'appium:automationName': 'UiAutomator2',
        webSocketUrl: true,
      },
    },
  });
  const socket = new WebSocket(capabilities.webSocketUrl);
  await new Promise((resolve, reject) => socket.once('open', resolve).once('error', reject));

  let streamId: string | undefined;
  let closing: Promise<void> | undefined;
  const shutdown = () =>
    (closing ??= (async () => {
      if (streamId) {
        await wd('POST', `/session/${sessionId}/execute/sync`, {
          script: 'mobile: stopMediaStream',
          args: [{streamId}],
        }).catch(() => {});
      }
      socket.close();
      try {
        await output.close();
      } finally {
        await wd('DELETE', `/session/${sessionId}`).catch(() => {});
      }
    })());
  const shutdownAndExit = (code: number) =>
    void shutdown()
      .then(
        () => code,
        (e: Error) => (console.error(e.message), 1),
      )
      .then((exitCode) => process.exit(exitCode));

  process.once('SIGINT', () => shutdownAndExit(0));
  void output.failure?.then(() => shutdownAndExit(1));
  socket.on('close', (code) => {
    // Appium closes the connection with 1013 if this client cannot keep up with the stream
    if (!closing) {
      console.log(`BiDi connection closed (${code})`);
      shutdownAndExit(1);
    }
  });
  socket.on('message', (raw) => {
    const {type, method, params} = JSON.parse(raw.toString());
    if (type !== 'event' || closing) {
      return;
    }
    if (method === 'appium:mediaStream.units') {
      for (const unit of params.units) {
        output.onUnit(unit.track, Buffer.from(unit.data, 'base64'), unit.key);
      }
    } else {
      // started, formatChanged (the frame size) and ended
      console.log(method, JSON.stringify(params));
    }
  });

  try {
    // Events are only emitted while a client is subscribed, so subscribe first
    await subscribe(socket, [MEDIA_STREAM_MODULE]);
    const stream = await wd('POST', `/session/${sessionId}/execute/sync`, {
      script: 'mobile: startMediaStream',
      args: [options.mode === 'mjpeg' ? {kind: 'jpeg', quality: 70} : {kind: 'video', codec, audio: options.audio}],
    });
    streamId = stream.streamId;
    console.log('Stream started', JSON.stringify(stream));
  } catch (e) {
    await shutdown().catch(() => {});
    throw e;
  }
}

main().catch((e) => {
  console.error(e instanceof Error ? e.message : e);
  process.exit(1);
});
