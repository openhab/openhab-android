#!/usr/bin/env bash

set -e

if $(echo "$TRAVIS_TAG" | grep -q "fdroid")
then
    echo "Tag for F-Droid detected. Nothing to do"
    exit 1
fi

gradle_file="mobile/build.gradle"

currentVersionCode=$(grep 'versionCode' $gradle_file | sed -r 's/(.*) (.*)$/\2/')
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
    VERSION_NAME="${TRAVIS_TAG%-release}"
    echo "New version code is $currentVersionCode and name $VERSION_NAME"
    echo "Replace versionCode"
    sed --in-place -r "s/versionCode (.*)/versionCode ${currentVersionCode}/" $gradle_file
    echo "Replace versionName"
    sed --in-place -r "s/versionName \"(.*)\"/versionName \"${VERSION_NAME}\"/" $gradle_file
fi
