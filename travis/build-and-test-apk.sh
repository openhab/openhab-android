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
