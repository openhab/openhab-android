#!/usr/bin/env bash

set -e

releaseFlavor="Stable"
if $(echo "$TRAVIS_TAG" | grep -q "beta")
then
    echo "Beta"
    releaseFlavor="Beta"
fi

echo "Build"
time ./gradlew :mobile:assembleFoss${releaseFlavor} :mobile:assembleFull${releaseFlavor}
echo "Unit tests"
time ./gradlew :mobile:testFoss${releaseFlavor}ReleaseUnitTest :mobile:testFull${releaseFlavor}ReleaseUnitTest
echo "Android tests"
time ./gradlew :mobile:connectedFoss${releaseFlavor}DebugAndroidTest :mobile:connectedFull${releaseFlavor}DebugAndroidTest
echo "Jacoco coverage reports"
time ./gradlew :mobile:jacocoTestFoss${releaseFlavor}DebugUnitTestReport :mobile:jacocoTestFull${releaseFlavor}DebugUnitTestReport
