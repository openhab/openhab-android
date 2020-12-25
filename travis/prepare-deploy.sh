#!/usr/bin/env bash

set -e

bash travis/bump-versioncode.sh

releaseFlavor="stable"
releaseFlavorCapital="Stable"

if $(echo "$TRAVIS_TAG" | grep -q "beta")
then
    echo "Beta"
    releaseFlavor="beta"
    releaseFlavorCapital="Beta"
fi
echo "Build apk"
time ./gradlew :mobile:assembleFull${releaseFlavorCapital}Release
echo "Sign apk"
openssl aes-256-cbc -K $encrypted_903a93ed2309_key -iv $encrypted_903a93ed2309_iv -in keystore.enc -out keystore -d
cp $TRAVIS_BUILD_DIR/keystore $HOME
mkdir $HOME/apks_to_deploy
cp mobile/build/outputs/apk/full${releaseFlavorCapital}/release/mobile-full-${releaseFlavor}-release-unsigned.apk $HOME/apks_to_deploy
cd $HOME/apks_to_deploy
jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 -keystore $HOME/keystore -storepass $storepass mobile-full-${releaseFlavor}-release-unsigned.apk sign > /dev/null
jarsigner -verify mobile-full-${releaseFlavor}-release-unsigned.apk > /dev/null
${ANDROID_HOME}/build-tools/25.0.2/zipalign -v 4 mobile-full-${releaseFlavor}-release-unsigned.apk openhab-android.apk > /dev/null
