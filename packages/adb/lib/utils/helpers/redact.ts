const SENSITIVE_ARGS = new Set(['--ks-pass', '--key-pass', '-storepass', '-keypass']);
const REDACTED = '******';

/**
 * Masks values of password-bearing command line arguments so the result is safe to log.
 *
 * @param args - The command line arguments
 * @returns A copy of the arguments with the secret values replaced
 */
export function redactSecrets(args: readonly string[]): string[] {
  return args.map((arg, i) => {
    if (i > 0 && SENSITIVE_ARGS.has(args[i - 1])) {
      return REDACTED;
    }
    const [name] = arg.split('=', 1);
    return SENSITIVE_ARGS.has(name) && arg.includes('=') ? `${name}=${REDACTED}` : arg;
  });
}
