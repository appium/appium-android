import path from 'node:path';

import {exec} from 'teen_process';

const EDGE_DRIVER_IDENTIFIER = 'msedgedriver';
const MICROSOFT_TEAM_ID = 'UBF8T346G9';
const SIGNATURE_CHECK_TIMEOUT_MS = 30000;
// Pinned to the Microsoft team and the driver identity, so other signed binaries do not pass
const MAC_REQUIREMENT =
  `anchor apple generic and certificate leaf[subject.OU] = "${MICROSOFT_TEAM_ID}" ` +
  `and identifier "${EDGE_DRIVER_IDENTIFIER}"`;
// The path is passed via the environment to avoid any quoting issues
const WINDOWS_PATH_ENV = 'APPIUM_CHROMEDRIVER_VERIFY_PATH';
const WINDOWS_SCRIPT =
  `$p = $env:${WINDOWS_PATH_ENV}; ` +
  `$sig = Get-AuthenticodeSignature -LiteralPath $p; ` +
  `$info = (Get-Item -LiteralPath $p).VersionInfo; ` +
  `[pscustomobject]@{status = [string]$sig.Status; subject = [string]$sig.SignerCertificate.Subject; ` +
  `original = [string]$info.OriginalFilename} | ConvertTo-Json -Compress`;

/**
 * Whether the file name is like the one of Microsoft Edge WebDriver (`msedgedriver`, `msedgedriver.exe`,
 * `msedgedriver_v157`, ...). It is only used to decide if a signature check is worth running.
 */
export function hasEdgeDriverFileName(binPath: string): boolean {
  return path.basename(binPath).toLowerCase().startsWith(EDGE_DRIVER_IDENTIFIER);
}

export interface SignatureCheckOptions {
  platform?: NodeJS.Platform;
  execFunc?: typeof exec;
}

/**
 * Verifies, without running the file, that it is a Microsoft Edge WebDriver signed by Microsoft.
 * Supported on macOS (code signature) and Windows (Authenticode). Linux binaries are not signed.
 * @throws {Error} If the signature is missing, invalid or not issued to the Edge WebDriver by Microsoft
 */
export async function assertSignedEdgeDriver(binPath: string, opts: SignatureCheckOptions = {}): Promise<void> {
  const {platform = process.platform, execFunc = exec} = opts;
  switch (platform) {
    case 'darwin':
      return await assertMacSignature(binPath, execFunc);
    case 'win32':
      return await assertWindowsSignature(binPath, execFunc);
    default:
      throw new Error(`Signatures of Microsoft Edge WebDriver cannot be verified on '${platform}'`);
  }
}

async function assertMacSignature(binPath: string, execFunc: typeof exec): Promise<void> {
  try {
    await execFunc('codesign', ['--verify', '--strict', `-R=${MAC_REQUIREMENT}`, binPath], {
      timeout: SIGNATURE_CHECK_TIMEOUT_MS,
    });
  } catch (e) {
    const err = e as Error & {stderr?: string};
    throw new Error(
      `The code signature is not a valid Microsoft Edge WebDriver one. ${err.stderr || err.message}`.trim(),
      {cause: e},
    );
  }
}

async function assertWindowsSignature(binPath: string, execFunc: typeof exec): Promise<void> {
  let stdout: string;
  try {
    ({stdout} = await execFunc('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command', WINDOWS_SCRIPT], {
      timeout: SIGNATURE_CHECK_TIMEOUT_MS,
      env: {...process.env, [WINDOWS_PATH_ENV]: binPath},
    }));
  } catch (e) {
    throw new Error(`Cannot verify the Authenticode signature. ${(e as Error).message}`, {cause: e});
  }
  let info: {status?: string; subject?: string; original?: string};
  try {
    info = JSON.parse(stdout);
  } catch (e) {
    throw new Error(`Cannot parse the Authenticode signature info: ${stdout}`, {cause: e});
  }
  if (info.status !== 'Valid') {
    throw new Error(`The Authenticode signature status is '${info.status}'`);
  }
  // O=Microsoft Corporation is part of the subject of the certificate issued to Microsoft
  if (!/(^|,\s*)O=Microsoft Corporation(,|$)/.test(info.subject ?? '')) {
    throw new Error(`The file is not signed by Microsoft Corporation (signed by '${info.subject}')`);
  }
  if ((info.original ?? '').toLowerCase() !== `${EDGE_DRIVER_IDENTIFIER}.exe`) {
    throw new Error(`The signed file is not Microsoft Edge WebDriver (original name '${info.original}')`);
  }
}
