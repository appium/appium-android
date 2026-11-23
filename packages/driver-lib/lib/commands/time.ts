import dayjs from 'dayjs';
import customParseFormat from 'dayjs/plugin/customParseFormat.js';
import utc from 'dayjs/plugin/utc.js';

import type {AndroidDriverConstraints} from '../constraints.js';
import type {AndroidDriver} from '../driver.js';

declare module '../driver.js' {
  interface AndroidDriver<C extends AndroidDriverConstraints> {
    getDeviceTime(...args: Parameters<typeof getDeviceTime>): ReturnType<typeof getDeviceTime>;
    mobileGetDeviceTime(...args: Parameters<typeof mobileGetDeviceTime>): ReturnType<typeof mobileGetDeviceTime>;
  }
}

dayjs.extend(utc);
dayjs.extend(customParseFormat);

const DATETIME_FORMAT_ISO8601 = 'YYYY-MM-DDTHH:mm:ssZ';
// Restrict characters to valid IANA TZ identifiers; this whitelist also keeps
// zoneName safe when adb shell re-parses the joined command string on device.
const IANA_TIME_ZONE_PATTERN = /^[A-Z][A-Za-z0-9_+-]{0,31}(?:\/[A-Z][A-Za-z0-9_+-]{0,31}){0,2}$/;

// Official IANA TZ identifiers and aliases omitted from Intl.supportedValuesOf('timeZone'),
// which only returns ICU's primary canonical keys.
const IANA_TIME_ZONE_ALIASES: readonly string[] = [
  'Africa/Asmara',
  'Africa/Timbuktu',
  'America/Argentina/Buenos_Aires',
  'America/Argentina/Catamarca',
  'America/Argentina/ComodRivadavia',
  'America/Argentina/Cordoba',
  'America/Argentina/Jujuy',
  'America/Argentina/Mendoza',
  'America/Atikokan',
  'America/Atka',
  'America/Ensenada',
  'America/Fort_Wayne',
  'America/Indiana/Indianapolis',
  'America/Kentucky/Louisville',
  'America/Knox_IN',
  'America/Montreal',
  'America/Nipigon',
  'America/Nuuk',
  'America/Pangnirtung',
  'America/Porto_Acre',
  'America/Rainy_River',
  'America/Rosario',
  'America/Santa_Isabel',
  'America/Shiprock',
  'America/Thunder_Bay',
  'America/Virgin',
  'America/Yellowknife',
  'Antarctica/South_Pole',
  'Asia/Ashkhabad',
  'Asia/Choibalsan',
  'Asia/Chongqing',
  'Asia/Chungking',
  'Asia/Dacca',
  'Asia/Harbin',
  'Asia/Ho_Chi_Minh',
  'Asia/Istanbul',
  'Asia/Kashgar',
  'Asia/Kathmandu',
  'Asia/Kolkata',
  'Asia/Macao',
  'Asia/Tel_Aviv',
  'Asia/Thimbu',
  'Asia/Ujung_Pandang',
  'Asia/Ulan_Bator',
  'Asia/Yangon',
  'Atlantic/Faroe',
  'Atlantic/Jan_Mayen',
  'Australia/ACT',
  'Australia/Canberra',
  'Australia/Currie',
  'Australia/LHI',
  'Australia/NSW',
  'Australia/North',
  'Australia/Queensland',
  'Australia/South',
  'Australia/Tasmania',
  'Australia/Victoria',
  'Australia/West',
  'Australia/Yancowinna',
  'Brazil/Acre',
  'Brazil/DeNoronha',
  'Brazil/East',
  'Brazil/West',
  'CET',
  'CST6CDT',
  'Canada/Atlantic',
  'Canada/Central',
  'Canada/Eastern',
  'Canada/Mountain',
  'Canada/Newfoundland',
  'Canada/Pacific',
  'Canada/Saskatchewan',
  'Canada/Yukon',
  'Chile/Continental',
  'Chile/EasterIsland',
  'Cuba',
  'EET',
  'EST',
  'EST5EDT',
  'Egypt',
  'Eire',
  'Etc/GMT',
  'Etc/GMT+0',
  'Etc/GMT+1',
  'Etc/GMT+10',
  'Etc/GMT+11',
  'Etc/GMT+12',
  'Etc/GMT+2',
  'Etc/GMT+3',
  'Etc/GMT+4',
  'Etc/GMT+5',
  'Etc/GMT+6',
  'Etc/GMT+7',
  'Etc/GMT+8',
  'Etc/GMT+9',
  'Etc/GMT-0',
  'Etc/GMT-1',
  'Etc/GMT-10',
  'Etc/GMT-11',
  'Etc/GMT-12',
  'Etc/GMT-13',
  'Etc/GMT-14',
  'Etc/GMT-2',
  'Etc/GMT-3',
  'Etc/GMT-4',
  'Etc/GMT-5',
  'Etc/GMT-6',
  'Etc/GMT-7',
  'Etc/GMT-8',
  'Etc/GMT-9',
  'Etc/GMT0',
  'Etc/Greenwich',
  'Etc/UCT',
  'Etc/UTC',
  'Etc/Universal',
  'Etc/Zulu',
  'Europe/Belfast',
  'Europe/Kyiv',
  'Europe/Nicosia',
  'Europe/Tiraspol',
  'Europe/Uzhgorod',
  'Europe/Zaporozhye',
  'GB',
  'GB-Eire',
  'GMT',
  'GMT+0',
  'GMT-0',
  'GMT0',
  'Greenwich',
  'HST',
  'Hongkong',
  'Iceland',
  'Iran',
  'Israel',
  'Jamaica',
  'Japan',
  'Kwajalein',
  'Libya',
  'MET',
  'MST',
  'MST7MDT',
  'Mexico/BajaNorte',
  'Mexico/BajaSur',
  'Mexico/General',
  'NZ',
  'NZ-CHAT',
  'Navajo',
  'PRC',
  'PST8PDT',
  'Pacific/Chuuk',
  'Pacific/Johnston',
  'Pacific/Kanton',
  'Pacific/Pohnpei',
  'Pacific/Samoa',
  'Pacific/Yap',
  'Poland',
  'Portugal',
  'ROC',
  'ROK',
  'Singapore',
  'Turkey',
  'UCT',
  'US/Alaska',
  'US/Aleutian',
  'US/Arizona',
  'US/Central',
  'US/East-Indiana',
  'US/Eastern',
  'US/Hawaii',
  'US/Indiana-Starke',
  'US/Michigan',
  'US/Mountain',
  'US/Pacific',
  'US/Samoa',
  'UTC',
  'Universal',
  'W-SU',
  'WET',
  'Zulu',
];

let knownTimeZones: ReadonlySet<string> | undefined;

/**
 * Gets the current device time.
 *
 * @param format The format string for the time. Defaults to ISO8601 format.
 * @returns Promise that resolves to the formatted device time string.
 */
export async function getDeviceTime(this: AndroidDriver, format: string = DATETIME_FORMAT_ISO8601): Promise<string> {
  this.log.debug(`Attempting to capture Android device date and time. The format specifier is '${format}'`);
  const deviceTimestamp = (await this.adb.shell(['date', '+%Y-%m-%dT%T%z'])).trim();
  this.log.debug(`Got device timestamp: ${deviceTimestamp}`);
  const parsedTimestamp = dayjs.utc(deviceTimestamp, 'YYYY-MM-DDTHH:mm:ssZZ');
  if (!parsedTimestamp.isValid()) {
    this.log.warn('Cannot parse the returned timestamp. Returning as is');
    return deviceTimestamp;
  }
  const offset = parseOffset(deviceTimestamp);
  return parsedTimestamp.utcOffset(offset).format(format);
}

/**
 * Gets the current device time.
 *
 * @param format The format string for the time. Defaults to 'YYYY-MM-DDTHH:mm:ssZ'.
 * @returns Promise that resolves to the formatted device time string.
 */
export async function mobileGetDeviceTime(this: AndroidDriver, format?: string): Promise<string> {
  return await this.getDeviceTime(format);
}

/**
 * Adjusts the device time zone.
 *
 * @param zoneName The time zone identifier (e.g., 'America/New_York', 'Europe/London').
 * @returns Promise that resolves when the time zone is set.
 * @throws {Error} If the time zone identifier is not known.
 */
export async function adjustTimeZone(this: AndroidDriver, zoneName: string): Promise<void> {
  if (!isValidTimeZone(zoneName)) {
    throw new Error(
      `The provided time zone identifier '${zoneName}' is not known. ` +
        `Please choose a valid TZ identifier from https://en.wikipedia.org/wiki/List_of_tz_database_time_zones`,
    );
  }
  this.log.info(`Setting the device time zone to '${zoneName}'`);
  // The magic number '3' depends on the actual ordering of methods in
  // the IAlarmManager interface and might be a subject of change between
  // different Android API versions.
  // See, for example,
  // https://cs.android.com/android/platform/superproject/+/master:frameworks/base/apex/jobscheduler/framework/java/android/app/IAlarmManager.aidl;l=1?q=IAlarmManager
  await this.adb.shell(['service', 'call', 'alarm', '3', 's16', zoneName]);
  try {
    const currentZone = (await this.adb.getTimeZone())?.trim();
    if (currentZone && currentZone !== zoneName) {
      this.log.warn(
        `The device time zone is '${currentZone}' after requesting '${zoneName}'. ` +
          `The device may not recognize '${zoneName}' or may have normalized it to another identifier`,
      );
    }
  } catch (e) {
    this.log.debug(`Unable to verify the device time zone after setting it: ${(e as Error).message}`);
  }
}

function parseOffset(timestamp: string): number {
  const m = timestamp.match(/([+-])(\d{2})(\d{2})$/);
  if (!m) {
    return 0;
  }
  const sign = m[1] === '+' ? 1 : -1;
  return sign * (Number(m[2]) * 60 + Number(m[3]));
}

function getKnownTimeZones(): ReadonlySet<string> {
  knownTimeZones ??= new Set([...Intl.supportedValuesOf('timeZone'), ...IANA_TIME_ZONE_ALIASES]);
  return knownTimeZones;
}

function isValidTimeZone(zoneName: string): boolean {
  if (typeof zoneName !== 'string' || !IANA_TIME_ZONE_PATTERN.test(zoneName)) {
    return false;
  }
  return getKnownTimeZones().has(zoneName);
}
