#!/usr/bin/env bash

set -e

releaseFlavor="stable"
releaseFlavorCapital="Stable"
packageName="org.openhab.habdroid"

if $(echo "$TRAVIS_TAG" | grep -q "beta")
then
    echo "Beta"
    releaseFlavor="beta"
    releaseFlavorCapital="Beta"
    packageName="org.openhab.habdroid.beta"
fi

sed -i -e /fabric/d -e '/google-services/d' build.gradle

echo "app_package_name('${packageName}')" >> fastlane/Screengrabfile
echo "tests_package_name('${packageName}')" >> fastlane/Screengrabfile
echo "app_apk_path('mobile/build/outputs/apk/full${releaseFlavorCapital}/debug/mobile-full-${releaseFlavor}-debug.apk')" >> fastlane/Screengrabfile
echo "tests_apk_path('mobile/build/outputs/apk/full${releaseFlavorCapital}/debug/mobile-full-${releaseFlavor}-debug.apk')" >> fastlane/Screengrabfile
echo "Screengrab"
time fastlane screengrab

git add . && git diff --staged
