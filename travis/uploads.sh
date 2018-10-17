#!/bin/bash

set -e

if [ -z "$TRAVIS_TAG" ]
then
    echo "No tag present, do nothing"
    exit
fi

source travis/bump-versioncode.sh

if $(echo "$TRAVIS_TAG" | grep -q "beta")
then
    python3 assets/store_descriptions/generate_and_validate.py fdroidBeta
else
    python3 assets/store_descriptions/generate_and_validate.py fdroid
fi

set +e
retryCount=0
until curl https://api.github.com/repos/openhab/openhab-android/releases | jq -r '.[0].body' > fastlane/metadata/android/en-US/changelogs/${currentVersionCode}.txt
do
    let retryCount++
    if [ "$retryCount" -gt 20 ]
    then
        exit 1
    fi
    echo "Download failed. Retry"
    sleep 5
done

set -e

git config --local user.name "openhab-bot"
git config --local user.email "bot@openhab.org"
echo "Git add"
git add fastlane/metadata/* "mobile/build.gradle"
echo "Git commit"
git commit -m "Bump version to $TRAVIS_TAG and update fastlane metadata"
echo "Git tag"
git tag -a "${TRAVIS_TAG}-fdroid" -m "${TRAVIS_TAG} for F-Droid"
echo "Git decrypt key"
openssl aes-256-cbc -K $encrypted_c0c05d762590_key -iv $encrypted_c0c05d762590_iv -in travis/key.enc -out travis/key -d > /dev/null 2>&1
echo "Copy ssh config"
cp travis/ssh-config ~/.ssh/config
echo "Change key permissions"
chmod 400 travis/key
echo "Git create master branch"
git checkout -b master
echo "Git add remote"
git remote add github git@github.com:openhab/openhab-android > /dev/null 2>&1
echo "Git fetch"
git fetch github > /dev/null 2>&1
echo "Git push master"
git push --quiet github master > /dev/null 2>&1
echo "Git push F-Droid tag"
git push --quiet github ${TRAVIS_TAG}-fdroid > /dev/null 2>&1
