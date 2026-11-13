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
 * if this `SettingsApp` instance has switched it on before; otherwise it is left as is.
 * If enabling fails midway, the already applied changes are rolled back before the error is thrown.
 *
 * @param enabled - True to enable and false to disable the service
 * @returns True if the state has actually been changed
 */
export async function setAccessibilityEventsServiceState(this: SettingsApp, enabled: boolean): Promise<boolean> {
  const [services, isToggleOn] = await Promise.all([getEnabledServices(this), isAccessibilityToggleOn(this)]);
  const isListed = services.includes(ACCESSIBILITY_EVENTS_SERVICE);

  if (enabled) {
    if (isListed && isToggleOn) {
      return false;
    }
    try {
      if (!isListed) {
        await this.adb.setSetting(
          'secure',
          ENABLED_SERVICES_SETTING,
          [...services, ACCESSIBILITY_EVENTS_SERVICE].join(':'),
        );
      }
      if (!isToggleOn) {
        this._accessibilityToggleWasOff = true;
        await this.adb.setSetting('secure', ACCESSIBILITY_ENABLED_SETTING, 1);
      }
    } catch (err) {
      // Do not leave a partially applied state behind, so the caller has nothing to clean up
      this._accessibilityToggleWasOff = false;
      if (!isListed) {
        await restoreEnabledServices(this, services);
      }
      throw err;
    }
  } else {
    if (!isListed) {
      return false;
    }
    const newServices = services.filter((s) => s !== ACCESSIBILITY_EVENTS_SERVICE);
    if (newServices.length > 0) {
      await this.adb.setSetting('secure', ENABLED_SERVICES_SETTING, newServices.join(':'));
    } else {
      await this.adb.shell(['settings', 'delete', 'secure', ENABLED_SERVICES_SETTING]);
    }
    if (this._accessibilityToggleWasOff) {
      await this.adb.setSetting('secure', ACCESSIBILITY_ENABLED_SETTING, 0);
    }
    this._accessibilityToggleWasOff = false;
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
  return isToggleOn && services.includes(ACCESSIBILITY_EVENTS_SERVICE);
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
