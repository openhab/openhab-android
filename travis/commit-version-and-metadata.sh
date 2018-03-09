#!/usr/bin/env bash

set -e

manifest="mobile/src/main/AndroidManifest.xml"

git config --local user.name "TravisCI"
git config --local user.email "support@openhab.org"
git add fastlane/*
git add $manifest
git commit -m "Bump version to $TRAVIS_TAG and update fastlane metadata"
git tag -a "${TRAVIS_TAG}-fdroid" -m "${TRAVIS_TAG} for F-Droid"
openssl aes-256-cbc -K $encrypted_c0c05d762590_key -iv $encrypted_c0c05d762590_iv -in travis/key.enc -out travis/key -d > /dev/null 2>&1
cp travis/ssh-config ~/.ssh/config
git remote add github git@github.com:openhab/openhab-android > /dev/null 2>&1
git push --quiet --set-upstream github :refs/heads/master > /dev/null 2>&1
