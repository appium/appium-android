import type {AndroidDriver} from '../../driver.js';

/**
 * Checks whether any BiDi client is subscribed to the event or to its whole module.
 *
 * @param driver - The driver instance
 * @param method - Fully qualified event name, e.g. `log.entryAdded`
 */
export function hasSubscribers(driver: AndroidDriver, method: string): boolean {
  const moduleName = method.split('.')[0];
  return [method, moduleName].some((e) => (driver.bidiEventSubs[e]?.length ?? 0) > 0);
}
