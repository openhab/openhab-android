#!/usr/bin/env bash

set -e

releaseFlavor="Stable"
if $(echo "$TRAVIS_TAG" | grep -q "beta")
then
    echo "Beta"
    releaseFlavor="Beta"
fi

echo "Build"
time ./gradlew :mobile:assemble{Foss,Full}${releaseFlavor}{Debug,Release}
echo "Unit tests"
time ./gradlew :mobile:test{Foss,Full}${releaseFlavor}ReleaseUnitTest
echo "Android tests"
time ./gradlew :mobile:connected{Foss,Full}${releaseFlavor}DebugAndroidTest
echo "Jacoco coverage reports"
time ./gradlew :mobile:jacocoTest{Foss,Full}${releaseFlavor}DebugUnitTestReport

echo "Screengrab"
time fastlane screengrab

git add . && git diff --staged
