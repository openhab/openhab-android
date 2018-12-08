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

bash travis/bump-versioncode.sh
for i in {1..5}; do bash travis/start-emulator.sh phone && break || sleep 15; done

./gradlew :mobile:assemble{Foss,Full}${releaseFlavorCapital}{Debug,Release} :mobile:test{Foss,Full}${releaseFlavorCapital}ReleaseUnitTest
for i in {1..2}; do ./gradlew :mobile:connected{Foss,Full}${releaseFlavorCapital}DebugAndroidTest && break || sleep 15; done
