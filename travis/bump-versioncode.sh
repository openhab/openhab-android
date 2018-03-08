#!/usr/bin/env bash

if $(echo "$TRAVIS_TAG" | grep -q "fdroid")
then
    echo "Tag for F-Droid detected. Nothing to do"
    exit 1
fi

manifest="mobile/src/main/AndroidManifest.xml"

currentVersionCode=$(grep 'android:versionCode' $manifest | sed -r 's/(.*)"(.*)"/\2/')
let currentVersionCode++

if [ -z "$currentVersionCode" ] || [ -z "$TRAVIS_TAG" ]
then
    echo "Code or tag are empty! Exiting..."
    exit 2
fi
sed --in-place -r "s/android:versionCode=\"(.*)\"/android:versionCode=\"${currentVersionCode}\"/" $manifest
sed --in-place -r "s/android:versionName=\"(.*)\"/android:versionName=\"${TRAVIS_TAG}\"/" $manifest
echo "New version code is $currentVersionCode and name $TRAVIS_TAG"
