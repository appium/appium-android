import type {SettingsApp} from '../client.js';
import {ACCESSIBILITY_EVENTS_SERVICE} from '../constants.js';
import {LOG_PREFIX} from '../logger.js';

const ENABLED_SERVICES_SETTING = 'enabled_accessibility_services';
const ACCESSIBILITY_ENABLED_SETTING = 'accessibility_enabled';

/**
 * Enables or disables the no-op accessibility service shipped with Appium Settings.
 *
 * Chrome emits no `AccessibilityEvent`s for web content if UiAutomation is the only
 * accessibility client, because it only dispatches event types requested by enabled
 * accessibility services. Having this service enabled fixes that. It is only effective
 * if the UiAutomation connection does not suppress accessibility services
 * (see the `DISABLE_SUPPRESS_ACCESSIBILITY_SERVICES` instrumentation argument of
 * appium-uiautomator2-server).
 *
 * Other enabled accessibility services are left untouched. The global accessibility toggle
 * (`accessibility_enabled`) is switched on if needed. It is only switched off again on disabling
 * if this `SettingsApp` instance has switched it on before and no other services have been
 * enabled since; otherwise it is left as is. A failed restoration is retried on the next disabling call.
 * If enabling fails midway, the already applied changes are rolled back before the error is thrown.
 *
 * @param enabled - True to enable and false to disable the service
 * @returns True if the state has actually been changed
 */
export async function setAccessibilityEventsServiceState(this: SettingsApp, enabled: boolean): Promise<boolean> {
  const [services, isToggleOn] = await Promise.all([getEnabledServices(this), isAccessibilityToggleOn(this)]);
  const isListed = services.some(isOurService);

  if (enabled) {
    if (isListed && isToggleOn) {
      return false;
    }
    // A pending restoration is only applicable while the toggle is still on
    const previousMarker = isToggleOn ? this._accessibilityToggleOffServices : null;
    try {
      if (!isListed) {
        await this.adb.setSetting(
          'secure',
          ENABLED_SERVICES_SETTING,
          [...services, ACCESSIBILITY_EVENTS_SERVICE].join(':'),
        );
      }
      if (!isToggleOn) {
        this._accessibilityToggleOffServices = services.filter((s) => !isOurService(s)).map(normalizeComponent);
        await this.adb.setSetting('secure', ACCESSIBILITY_ENABLED_SETTING, 1);
      }
    } catch (err) {
      // Do not leave a partially applied state behind, so the caller has nothing to clean up
      this._accessibilityToggleOffServices = previousMarker;
      if (!isListed) {
        await restoreEnabledServices(this, services);
      }
      throw err;
    }
  } else {
    const newServices = services.filter((s) => !isOurService(s));
    if (isListed) {
      if (newServices.length > 0) {
        await this.adb.setSetting('secure', ENABLED_SERVICES_SETTING, newServices.join(':'));
      } else {
        await this.adb.shell(['settings', 'delete', 'secure', ENABLED_SERVICES_SETTING]);
      }
    }
    // Also retries a toggle restoration that failed on a previous call
    const isToggleRestored = await restorePendingToggle(this, newServices, isToggleOn);
    if (!isListed && !isToggleRestored) {
      return false;
    }
  }
  this.log.debug(
    LOG_PREFIX,
    `${enabled ? 'Enabled' : 'Disabled'} accessibility service '${ACCESSIBILITY_EVENTS_SERVICE}'`,
  );
  return true;
}

/**
 * @returns True if the accessibility service of Appium Settings is enabled and
 * accessibility is switched on globally
 */
export async function isAccessibilityEventsServiceEnabled(this: SettingsApp): Promise<boolean> {
  const [services, isToggleOn] = await Promise.all([getEnabledServices(this), isAccessibilityToggleOn(this)]);
  return isToggleOn && services.some(isOurService);
}

/**
 * Switches the global accessibility toggle off if it has been switched on by this instance
 * and no other services have been enabled since. The marker is kept if the write fails,
 * so the restoration can be retried.
 *
 * @returns True if the toggle has actually been switched off
 */
async function restorePendingToggle(
  settingsApp: SettingsApp,
  services: string[],
  isToggleOn: boolean,
): Promise<boolean> {
  const initialServices = settingsApp._accessibilityToggleOffServices;
  if (!initialServices) {
    return false;
  }
  const shouldRestore = isToggleOn && services.every((s) => initialServices.includes(normalizeComponent(s)));
  if (shouldRestore) {
    await settingsApp.adb.setSetting('secure', ACCESSIBILITY_ENABLED_SETTING, 0);
  }
  settingsApp._accessibilityToggleOffServices = null;
  return shouldRestore;
}

async function restoreEnabledServices(settingsApp: SettingsApp, services: string[]): Promise<void> {
  try {
    if (services.length > 0) {
      await settingsApp.adb.setSetting('secure', ENABLED_SERVICES_SETTING, services.join(':'));
    } else {
      await settingsApp.adb.shell(['settings', 'delete', 'secure', ENABLED_SERVICES_SETTING]);
    }
  } catch (e) {
    settingsApp.log.warn(LOG_PREFIX, `Cannot roll back the enabled accessibility services: ${(e as Error).message}`);
  }
}

async function getEnabledServices(settingsApp: SettingsApp): Promise<string[]> {
  const output = (await settingsApp.adb.getSetting('secure', ENABLED_SERVICES_SETTING)).trim();
  return !output || output === 'null' ? [] : output.split(':').filter(Boolean);
}

async function isAccessibilityToggleOn(settingsApp: SettingsApp): Promise<boolean> {
  return (await settingsApp.adb.getSetting('secure', ACCESSIBILITY_ENABLED_SETTING)).trim() === '1';
}

/** Expands the short `pkg/.Cls` form to `pkg/pkg.Cls`, as stored by `ComponentName.flattenToString()` */
function normalizeComponent(component: string): string {
  const [pkg, cls] = component.split('/');
  return cls?.startsWith('.') ? `${pkg}/${pkg}${cls}` : component;
}

function isOurService(component: string): boolean {
  return normalizeComponent(component) === normalizeComponent(ACCESSIBILITY_EVENTS_SERVICE);
}
