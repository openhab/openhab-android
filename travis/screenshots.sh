#!/bin/bash

if [ -z "$TRAVIS_TAG" ]
then
    echo "No tag present, do nothing"
    #exit
fi

bash travis/bump-versioncode.sh

if $(echo "$TRAVIS_TAG" | grep -q "beta")
then
    bash assets/store_descriptions/generate_and_validate.sh fdroidBeta
else
    bash assets/store_descriptions/generate_and_validate.sh fdroid
fi

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

patch=$(echo "$TRAVIS_TAG" | sed -r 's/([0-9]+)\.([0-9]+)\.([0-9]+)-(.*)/\3/')
patch=10
if [ $((patch%3)) -eq 0 ]
then
    bash travis/start-emulator.sh phone
    echo "Run screengrab on a phone"
elif [ $((patch%3)) -eq 1 ]
then
    bash travis/start-emulator.sh tenInch
    echo "Run screengrab on a tenInch"
else
    bash travis/start-emulator.sh sevenInch
    echo "Run screengrab on a sevenInch"
fi
time fastlane screengrab


#bash travis/start-emulator.sh tenInch
#echo "Run screengrab on a tablet"
#time fastlane screengrab

shopt -s globstar
rename -n "s/(.*)_(.*).png$/\$1.png/" fastlane/metadata/android/**
rename "s/(.*)_(.*).png$/\$1.png/" fastlane/metadata/android/**
cp fastlane/metadata/android/en-US/images/phoneScreenshots/{main-menu,widget-overview}.png docs/images

git config --local user.name "openhab-bot"
git config --local user.email "bot@openhab.org"
echo "Git add"
git add fastlane/* docs/* "mobile/src/main/AndroidManifest.xml"
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
