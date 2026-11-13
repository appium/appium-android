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
 * enabled since; otherwise it is left as is.
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
  } else {
    if (!isListed) {
      return false;
    }
    const newServices = services.filter((s) => !isOurService(s));
    if (newServices.length > 0) {
      await this.adb.setSetting('secure', ENABLED_SERVICES_SETTING, newServices.join(':'));
    } else {
      await this.adb.shell(['settings', 'delete', 'secure', ENABLED_SERVICES_SETTING]);
    }
    const initialServices = this._accessibilityToggleOffServices;
    if (initialServices && newServices.every((s) => initialServices.includes(normalizeComponent(s)))) {
      await this.adb.setSetting('secure', ACCESSIBILITY_ENABLED_SETTING, 0);
    }
    this._accessibilityToggleOffServices = null;
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
