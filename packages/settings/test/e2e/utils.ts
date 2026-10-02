/**
 * Feeds items from `source` to `visit` until it returns true or `timeoutMs` elapses.
 * Each pull is raced against the deadline: a stalled stream would otherwise block a
 * `for await` loop forever, since its own deadline check only runs once an item arrives.
 * Reuse one iterator across calls - a timed-out pull stays pending and would swallow the next item.
 *
 * @returns True if `visit` accepted an item, false on timeout or end of stream
 */
export async function consumeUntil<T>(
  source: AsyncIterator<T>,
  timeoutMs: number,
  visit: (item: T) => boolean | void,
): Promise<boolean> {
  const deadline = Date.now() + timeoutMs;
  for (;;) {
    const remainingMs = deadline - Date.now();
    if (remainingMs <= 0) {
      return false;
    }
    let timer: NodeJS.Timeout | undefined;
    const timedOut = new Promise<null>((resolve) => {
      timer = setTimeout(() => resolve(null), remainingMs);
    });
    const result = await Promise.race([source.next(), timedOut]).finally(() => clearTimeout(timer));
    if (result === null || result.done) {
      return false;
    }
    if (visit(result.value)) {
      return true;
    }
  }
}
