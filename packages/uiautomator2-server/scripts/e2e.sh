#!/bin/bash -xe

adb shell settings put global hidden_api_policy  1
adb shell settings put global hidden_api_policy_pre_p_apps  1
adb shell settings put global hidden_api_policy_p_apps 1
# Newer system images default to gesture navigation. A swipe that starts or ends near the
# bottom edge then becomes the system home gesture, which breaks scroll tests in landscape.
adb shell cmd overlay enable com.android.internal.systemui.navbar.threebutton || true

# Sharding (CI): SHARD_COUNT shards, this one is SHARD_INDEX (1-based). Defaults run everything.
shard_count=${SHARD_COUNT:-1}
shard_index=$(( ${SHARD_INDEX:-1} - 1 ))
pkg=io.appium.uiautomator2.unittest.test

run_class() {
  local args=(-Pandroid.testInstrumentationRunnerArguments.class=$pkg.$1)
  rm -rf app/build/outputs/androidTest-results/connected
  if [[ $2 == split && $shard_count -gt 1 ]]; then
    args+=(-Pandroid.testInstrumentationRunnerArguments.numShards=$shard_count
           -Pandroid.testInstrumentationRunnerArguments.shardIndex=$shard_index)
  fi
  local status=0
  ./gradlew connectedE2eTestDebugAndroidTest "${args[@]}" || status=$?
  # Gradle only reports "There were failing tests", so list the per-test results
  node ./scripts/print-test-results.mjs || true
  return $status
}

# Small classes run whole on one shard (round-robin); the slowest one is split across all shards by test
whole_classes=(ActionsCommandsTest AlertCommandsTest ElementCommandsTest ClassRegexCommandsTest
               ClassChainCommandsTest)
did_fail=0
for i in "${!whole_classes[@]}"; do
  if (( i % shard_count == shard_index )) && ! run_class "${whole_classes[$i]}" whole; then
    did_fail=1
  fi
done
if ! run_class DeviceCommandsTest split; then
  did_fail=1
fi

if [[ did_fail -eq 1 ]]; then
  exit 1
fi
