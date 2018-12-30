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
bash travis/start-emulator.sh phone

./gradlew :mobile:assemble{Foss,Full}${releaseFlavorCapital}{Debug,Release} :mobile:test{Foss,Full}${releaseFlavorCapital}ReleaseUnitTest
retryCount=0
while true
do
    ./gradlew :mobile:connected{Foss,Full}${releaseFlavorCapital}DebugAndroidTest && break
    echo "Build failed. Retry..."
    ((retryCount+=1))
    if [ "$retryCount" -gt 3 ]
    then
        echo "Max. retry count reached. Exiting..."
        exit 1
    fi
done
