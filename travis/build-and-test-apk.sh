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

echo "Build"
time ./gradlew :mobile:assemble{Foss,Full}${releaseFlavorCapital}{Debug,Release}
echo "Unit tests"
time ./gradlew :mobile:test{Foss,Full}${releaseFlavorCapital}ReleaseUnitTest
echo "Android tests"
time ./gradlew :mobile:connected{Foss,Full}${releaseFlavorCapital}DebugAndroidTest
echo "Jacoco coverage reports"
time ./gradlew :mobile:jacocoTest{Foss,Full}${releaseFlavorCapital}DebugUnitTestReport

echo "app_package_name('${packageName}')" >> fastlane/Screengrabfile
echo "app_apk_path('mobile/build/outputs/apk/full${releaseFlavorCapital}/debug/mobile-full-${releaseFlavor}-debug.apk')" >> fastlane/Screengrabfile
echo "tests_apk_path('mobile/build/outputs/apk/full${releaseFlavorCapital}/debug/mobile-full-${releaseFlavor}-debug.apk')" >> fastlane/Screengrabfile
echo "Screengrab"
time fastlane screengrab

git add . && git diff --staged
