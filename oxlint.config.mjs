import appiumConfig, {defineConfig, ignorePatterns} from '@appium/oxc-config/oxlint';

export default defineConfig({
  extends: [appiumConfig],
  ignorePatterns: [...ignorePatterns, 'packages/adb/keys/**', 'packages/uiautomator2-server/{app,gradle,vendor}/**'],
});
