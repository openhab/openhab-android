#!/bin/bash

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

echo "Stop phone emulator"
adb devices | grep emulator | cut -f1 | while read line; do adb -s $line emu kill; done

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
echo "Run screengrab"
echo "device_type('tenInch')" >> fastlane/Screengrabfile
time fastlane screengrab

shopt -s globstar
rename -n "s/(.*)_(.*).png$/\$1.png/" fastlane/metadata/android/**
rename "s/(.*)_(.*).png$/\$1.png/" fastlane/metadata/android/**
cp fastlane/metadata/android/en-US/images/phoneScreenshots/{main-menu,widget-overview}.png docs/images

echo "Git add"
git add fastlane/* docs/* "mobile/src/main/AndroidManifest.xml"
echo "Git commit"
git commit -m "Bump version to $TRAVIS_TAG and update fastlane metadata"
