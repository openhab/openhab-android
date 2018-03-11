#!/usr/bin/env bash

set -e

manifest="mobile/src/main/AndroidManifest.xml"

echo "Configure git"
git config --local user.name "openhab-bot"
git config --local user.email "bot@openhab.org"
echo "Git add"
git add fastlane/*
git add $manifest
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

releaseFlavor="stable"
if $(echo "$TRAVIS_TAG" | grep -q "beta")
then
    echo "Beta"
    releaseFlavor="beta"
fi

openssl aes-256-cbc -K $encrypted_903a93ed2309_key -iv $encrypted_903a93ed2309_iv -in keystore.enc -out keystore -d
cp $TRAVIS_BUILD_DIR/keystore $HOME
mkdir $HOME/apks_to_deploy
cp mobile/build/outputs/apk/full${releaseFlavor}/release/mobile-full-${releaseFlavor}-release-unsigned.apk $HOME/apks_to_deploy
cp mobile/build/outputs/apk/foss${releaseFlavor}/release/mobile-foss-${releaseFlavor}-release-unsigned.apk $HOME/apks_to_deploy
cd $HOME/apks_to_deploy
jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 -keystore $HOME/keystore -storepass $storepass mobile-full-${releaseFlavor}-release-unsigned.apk sign
jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 -keystore $HOME/keystore -storepass $storepass mobile-foss-${releaseFlavor}-release-unsigned.apk sign
jarsigner -verify mobile-full-${releaseFlavor}-release-unsigned.apk
jarsigner -verify mobile-foss-${releaseFlavor}-release-unsigned.apk
${ANDROID_HOME}/build-tools/25.0.2/zipalign -v 4 mobile-full-${releaseFlavor}-release-unsigned.apk openhab-android.apk
${ANDROID_HOME}/build-tools/25.0.2/zipalign -v 4 mobile-foss-${releaseFlavor}-release-unsigned.apk openhab-android-foss.apk
