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
 * Other enabled accessibility services are left untouched.
 *
 * @param enabled - True to enable and false to disable the service
 * @returns True if the state has actually been changed
 */
export async function setAccessibilityEventsServiceState(this: SettingsApp, enabled: boolean): Promise<boolean> {
  const services = await getEnabledServices(this);
  const isEnabled = services.includes(ACCESSIBILITY_EVENTS_SERVICE);
  if (enabled === isEnabled) {
    return false;
  }

  const newServices = enabled
    ? [...services, ACCESSIBILITY_EVENTS_SERVICE]
    : services.filter((s) => s !== ACCESSIBILITY_EVENTS_SERVICE);
  if (newServices.length > 0) {
    await this.adb.setSetting('secure', ENABLED_SERVICES_SETTING, newServices.join(':'));
  } else {
    await this.adb.shell(['settings', 'delete', 'secure', ENABLED_SERVICES_SETTING]);
  }
  await this.adb.setSetting('secure', ACCESSIBILITY_ENABLED_SETTING, newServices.length > 0 ? 1 : 0);
  this.log.debug(
    LOG_PREFIX,
    `${enabled ? 'Enabled' : 'Disabled'} accessibility service '${ACCESSIBILITY_EVENTS_SERVICE}'`,
  );
  return true;
}

/**
 * @returns True if the accessibility service of Appium Settings is currently enabled
 */
export async function isAccessibilityEventsServiceEnabled(this: SettingsApp): Promise<boolean> {
  return (await getEnabledServices(this)).includes(ACCESSIBILITY_EVENTS_SERVICE);
}

async function getEnabledServices(settingsApp: SettingsApp): Promise<string[]> {
  const output = (await settingsApp.adb.getSetting('secure', ENABLED_SERVICES_SETTING)).trim();
  return !output || output === 'null' ? [] : output.split(':').filter(Boolean);
}
