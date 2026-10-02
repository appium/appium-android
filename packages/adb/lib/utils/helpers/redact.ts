const SENSITIVE_ARGS = new Set(['--ks-pass', '--key-pass', '-storepass', '-keypass']);
const LOCK_CREDENTIAL_VERBS = new Set(['verify', 'clear', 'set-pin', 'set-password', 'set-pattern']);
// Matches a `locksettings` credential command embedded in a single (e.g. `su root "..."`) argument
const LOCK_COMMAND_RE = /\blocksettings\s+(verify|clear|set-pin|set-password|set-pattern)\b[\s\S]*$/;
const REDACTED = '******';

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
