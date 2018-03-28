#!/usr/bin/env bash

set -e

releaseFlavor="stable"
releaseFlavorCaptital="Stable"

if $(echo "$TRAVIS_TAG" | grep -q "beta")
then
    echo "Beta"
    releaseFlavor="beta"
    releaseFlavorCaptital="Beta"
fi

echo "Sign apk"
openssl aes-256-cbc -K $encrypted_903a93ed2309_key -iv $encrypted_903a93ed2309_iv -in keystore.enc -out keystore -d
cp $TRAVIS_BUILD_DIR/keystore $HOME
mkdir $HOME/apks_to_deploy
cp mobile/build/outputs/apk/full${releaseFlavorCaptital}/release/mobile-full-${releaseFlavor}-release-unsigned.apk $HOME/apks_to_deploy
cp mobile/build/outputs/apk/foss${releaseFlavorCaptital}/release/mobile-foss-${releaseFlavor}-release-unsigned.apk $HOME/apks_to_deploy
cd $HOME/apks_to_deploy
jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 -keystore $HOME/keystore -storepass $storepass mobile-full-${releaseFlavor}-release-unsigned.apk sign > /dev/null
jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 -keystore $HOME/keystore -storepass $storepass mobile-foss-${releaseFlavor}-release-unsigned.apk sign > /dev/null
jarsigner -verify mobile-full-${releaseFlavor}-release-unsigned.apk > /dev/null
jarsigner -verify mobile-foss-${releaseFlavor}-release-unsigned.apk > /dev/null
${ANDROID_HOME}/build-tools/25.0.2/zipalign -v 4 mobile-full-${releaseFlavor}-release-unsigned.apk openhab-android.apk > /dev/null
${ANDROID_HOME}/build-tools/25.0.2/zipalign -v 4 mobile-foss-${releaseFlavor}-release-unsigned.apk openhab-android-foss.apk > /dev/null
cd -

echo "Configure git"
git config --local user.name "openhab-bot"
git config --local user.email "bot@openhab.org"
echo "Check for beta version"
if $(echo "$TRAVIS_TAG" | grep -q "beta")
then
    bash assets/store_descriptions/generate_and_validate.sh fdroidBeta
else
    bash assets/store_descriptions/generate_and_validate.sh fdroid
fi

echo "Start tablet emulator"
echo no | android create avd --force -n tablet -t "android-22" --abi armeabi-v7a -d "10.1in WXGA (Tablet)"
emulator -avd tablet -no-audio -no-window &
android-wait-for-emulator
adb shell input keyevent 82 &
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command battery -e plugged false -e level 100
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
adb shell am broadcast -a com.android.systemui.demo -e command status -e location hide -e alarm hide -e volume hide
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0815
adb shell am broadcast -a com.android.systemui.demo -e command network -e mobile show -e datatype 4g -e level 4 -e wifi show -e fully true -e level 3

# Workaround for #794: Remove crashalytics
sed -i -e 's|maven.fabric.io/public|repo1.maven.org/maven2|' -e /fabric/d -e '/google-services/d' mobile/build.gradle
echo "Build apks for screengrab"
time ./gradlew assembleFullStable assembleAndroidTest
echo "Configure locales for fastlane"
locales=$(find assets/store_descriptions/*/strings.sh -type f -exec wc -l {} \; | grep -v '^13' | cut -d '/' -f 3 | sed "s/$/', /" | tr '\n' "'")
locales="${locales%, \'}"
locales="locales [ '$locales ]"
echo "Locales: $locales"
echo $locales >> fastlane/Screengrabfile
echo "Install fastlane"
gem install fastlane screengrab
echo "Run screengrab"
time fastlane screengrab

echo "Git add"
git add fastlane/* "mobile/src/main/AndroidManifest.xml"
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

cd $HOME/apks_to_deploy
