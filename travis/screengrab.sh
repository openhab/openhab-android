#!/usr/bin/env bash

sed -i -e 's|maven.fabric.io/public|repo1.maven.org/maven2|' -e /fabric/d -e '/google-services/d' mobile/build.gradle

./gradlew assembleFullStable assembleAndroidTest

time fastlane screengrab

git add . && git diff --staged

adb shell pm list instrumentation
