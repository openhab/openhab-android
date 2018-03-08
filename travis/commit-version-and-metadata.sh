#!/usr/bin/env bash

manifest="mobile/src/main/AndroidManifest.xml"

git config --local user.name "TravisCI"
git config --local user.email "support@openhab.org"
git add fastlane/*
git add $manifest
git commit -m "Bump version to $TRAVIS_TAG and update fastlane metadata"
git tag -a "${TRAVIS_TAG}-fdroid" -m "${TRAVIS_TAG} for F-Droid"
git push origin :refs/heads/master # Add github token here
