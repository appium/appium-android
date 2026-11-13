import appiumConfig, {defineConfig, ignorePatterns} from '@appium/oxc-config/oxfmt';

export default defineConfig({
  ...appiumConfig,
  ignorePatterns: [
    ...ignorePatterns,
    'packages/adb/keys/**',
    'packages/uiautomator2-server/{app,gradle}/**',
    'packages/xpath2-processor/{bom,gradle,org.*}/**',
  ],
});
