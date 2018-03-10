#!/usr/bin/env bash

set -e

if $(echo "$TRAVIS_TAG" | grep -q "fdroid")
then
    echo "Tag for F-Droid detected. Nothing to do"
    exit 1
fi

manifest="mobile/src/main/AndroidManifest.xml"

currentVersionCode=$(grep 'android:versionCode' $manifest | sed -r 's/(.*)"(.*)"/\2/')
let currentVersionCode++

if [ -z "$TRAVIS_TAG" ]
then
    TRAVIS_TAG=$(git rev-parse HEAD)
    echo "Tag is empty, use git hash ($TRAVIS_TAG) instead"
fi

if [ -z "$currentVersionCode" ] || [ -z "$TRAVIS_TAG" ]
then
    echo "Code ($currentVersionCode) or tag ($TRAVIS_TAG) are empty! Don't bump anything."
else
    # Remove -release from version name for stable versions
    TRAVIS_TAG="${TRAVIS_TAG%-release}"
    echo "New version code is $currentVersionCode and name $TRAVIS_TAG"
    echo "Replace versionCode"
    sed --in-place -r "s/android:versionCode=\"(.*)\"/android:versionCode=\"${currentVersionCode}\"/" $manifest
    echo "Replace versionName"
    sed --in-place -r "s/android:versionName=\"(.*)\"/android:versionName=\"${TRAVIS_TAG}\"/" $manifest
fi
