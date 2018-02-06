#!/usr/bin/env bash

if $(echo "$TRAVIS_TAG" | grep -q  "fdroid")
then
    echo "Tag for F-Droid detected. Do nothing"
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

git config --local user.name "TravisCI"
git config --local user.email "support@openhab.org"
git add fastlane/*
git add $manifest
git commit -m "Bump version to $TRAVIS_TAG and update fastlane metadata"
git tag -a "${TRAVIS_TAG}-fdroid" -m "${TRAVIS_TAG} for F-Droid"
#git push origin :refs/heads/master
