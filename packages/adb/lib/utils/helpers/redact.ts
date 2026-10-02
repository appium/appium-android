import {util} from '@appium/support';

const SENSITIVE_ARGS = new Set(['--ks-pass', '--key-pass', '-storepass', '-keypass']);
const LOCK_CREDENTIAL_VERBS = new Set(['verify', 'clear', 'set-pin', 'set-password', 'set-pattern']);
// Matches a `locksettings` credential command embedded in a single (e.g. `su root "..."`) argument
const LOCK_COMMAND_RE = /\blocksettings\s+(verify|clear|set-pin|set-password|set-pattern)\b[\s\S]*$/;
const REDACTED = 'REDACTED';

/**
 * Masks secrets in command line arguments (keystore passwords and lock screen credentials)
 * so the result is safe to log.
 *
 * @param args - The command line arguments
 * @returns A copy of the arguments with the secret values replaced
 */
export function redactSecrets(args: readonly string[]): string[] {
  let isLockCredentialPart = false;
  return args.map((arg, i) => {
    if (isLockCredentialPart) {
      return arg === '--old' ? arg : REDACTED;
    }
    if (args[i - 1] === 'locksettings' && LOCK_CREDENTIAL_VERBS.has(arg)) {
      isLockCredentialPart = true;
      return arg;
    }
    if (i > 0 && SENSITIVE_ARGS.has(args[i - 1])) {
      return REDACTED;
    }
    const [name] = arg.split('=', 1);
    if (SENSITIVE_ARGS.has(name) && arg.includes('=')) {
      return `${name}=${REDACTED}`;
    }
    return arg.replace(LOCK_COMMAND_RE, `locksettings $1 ${REDACTED}`);
  });
}

/**
 * Masks secrets in the message and stack of an error thrown by `teen_process.exec`,
 * which embed the full command line. The error is modified in place.
 *
 * @param err - The caught error
 * @param cmd - The executable passed to `exec`
 * @param args - The arguments passed to `exec`
 * @returns The same error, so it can be rethrown as `throw redactExecError(...)`
 */
export function redactExecError<T>(err: T, cmd: string, args: readonly string[]): T {
  if (!(err instanceof Error)) {
    return err;
  }
  const rawCmd = util.quote([cmd, ...args]);
  const safeCmd = util.quote(redactSecrets([cmd, ...args]));
  if (rawCmd !== safeCmd) {
    err.message = err.message.split(rawCmd).join(safeCmd);
    if (err.stack) {
      err.stack = err.stack.split(rawCmd).join(safeCmd);
    }
  }
  return err;
}
