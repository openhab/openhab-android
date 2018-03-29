#!/bin/bash

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

time ./gradlew :mobile:jacocoTest{Foss,Full}${releaseFlavorCapital}DebugUnitTestReport
time bash <(curl -s https://codecov.io/bash)
