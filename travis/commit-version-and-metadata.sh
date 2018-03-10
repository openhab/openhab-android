#!/usr/bin/env bash

set -e

manifest="mobile/src/main/AndroidManifest.xml"

echo "Configure git"
git config --local user.name "TravisCI"
git config --local user.email "support@openhab.org"
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
echo "Git add remote"
git remote add github git@github.com:openhab/openhab-android
echo "Git fetch"
git fetch github
echo "Git push"
git push --quiet github master
