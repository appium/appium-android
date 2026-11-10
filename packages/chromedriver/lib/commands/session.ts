import {isStandardCap} from '@appium/base-driver';
import {util} from '@appium/support';
import * as semver from 'semver';

import {CHROMEDRIVER_EVENTS, CHROMEDRIVER_STATES} from '../constants.js';
import {generateLogPrefix} from '../utils.js';
import type {ChromedriverCommandContext} from './types.js';

// The first release with the (opt-in) W3C mode, and the first one that enables it by default
const MIN_CD_VERSION_WITH_W3C_SUPPORT = '2.32.0';
const MIN_CD_VERSION_WITH_W3C_BY_DEFAULT = 75;
const W3C_PREFIX = 'goog:';

export type SessionCapabilities = Record<string, any>;

/**
 * Converts a capability name to W3C format by adding the 'goog:' prefix if needed.
 */
export function toW3cCapName(capName: string): string {
  return typeof capName === 'string' && !capName.includes(':') && !isStandardCap(capName)
    ? `${W3C_PREFIX}${capName}`
    : capName;
}

/**
 * Gets a capability value from a capabilities object, handling both standard and W3C format names.
 */
export function getCapValue(allCaps: Record<string, any> = {}, rawCapName: string, defaultValue?: any): any {
  for (const [capName, capValue] of Object.entries(allCaps)) {
    if (toW3cCapName(capName) === toW3cCapName(rawCapName)) {
      return capValue;
    }
  }
  return defaultValue;
}

/**
 * Converts all capability names in an object to W3C format.
 */
export function toW3cCapNames(originalCaps: Record<string, any> = {}): Record<string, any> {
  return Object.fromEntries(Object.entries(originalCaps).map(([key, value]) => [toW3cCapName(key), value]));
}

/**
 * Creates a new W3C Chromedriver session.
 */
export async function startSession(this: ChromedriverCommandContext): Promise<SessionCapabilities> {
  applyW3cCapabilityQuirks.call(this);
  const sessionCaps = {capabilities: {alwaysMatch: toW3cCapNames(this.capabilities)}};
  this.log.info(`Starting W3C Chromedriver session with capabilities: ` + JSON.stringify(sessionCaps, null, 2));
  const response = (await this.jwproxy.command('/session', 'POST', sessionCaps)) as Record<string, any>;
  this.log.prefix = generateLogPrefix(this);
  changeState.call(this, CHROMEDRIVER_STATES.ONLINE);
  return response?.capabilities ?? response;
}

/**
 * Validates the driver version and adjusts capabilities for drivers with opt-in W3C support.
 * The legacy JSONWP protocol is no longer supported, so the W3C protocol is always requested.
 */
function applyW3cCapabilityQuirks(this: ChromedriverCommandContext): void {
  let needsExplicitW3c = false;
  if (this.driverVersion) {
    const coercedVersion = semver.coerce(this.driverVersion);
    if (!coercedVersion) {
      this.log.warn(`Cannot parse the ChromeDriver version '${this.driverVersion}'. Assuming W3C protocol support.`);
    } else if (semver.lt(coercedVersion, MIN_CD_VERSION_WITH_W3C_SUPPORT)) {
      throw new Error(
        `The ChromeDriver v. ${this.driverVersion} does not support the W3C WebDriver protocol. ` +
          `Only versions ${MIN_CD_VERSION_WITH_W3C_SUPPORT}+ are supported, since the legacy JSONWP protocol is no longer supported.`,
      );
    } else if (coercedVersion.major < MIN_CD_VERSION_WITH_W3C_BY_DEFAULT) {
      this.log.warn(
        `The ChromeDriver v. ${this.driverVersion} only supports the W3C WebDriver protocol in the opt-in mode, ` +
          `which might be incomplete. Enabling it explicitly.`,
      );
      needsExplicitW3c = true;
    }
  }

  const chromeOptions = getCapValue(this.capabilities, 'chromeOptions');
  if (util.isPlainObject(chromeOptions) && chromeOptions.w3c === false) {
    this.log.warn(`The 'chromeOptions.w3c: false' capability is no longer supported. Forcing the W3C protocol.`);
    needsExplicitW3c = true;
  }

  const statusMsg = this._onlineStatus?.message;
  // OperaDriver requires explicit W3C request or it falls back to JWP.
  if (typeof statusMsg === 'string' && statusMsg.includes('OperaDriver')) {
    needsExplicitW3c = true;
  }

  if (needsExplicitW3c) {
    if (util.isPlainObject(chromeOptions)) {
      chromeOptions.w3c = true;
    } else {
      this.capabilities[toW3cCapName('chromeOptions')] = {w3c: true};
    }
  }
}

/**
 * Updates driver state and emits state-change event payload.
 */
export function changeState(this: ChromedriverCommandContext, state: string): void {
  this.state = state;
  this.log.debug(`Changed state to '${state}'`);
  this.emit(CHROMEDRIVER_EVENTS.CHANGED, {state});
}
