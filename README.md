<p align="center">
    <a href="https://github.com/openhab/openhab-android/actions?query=workflow%3A%22Build+App%22"><img alt="GitHub Action" src="https://github.com/openhab/openhab-android/workflows/Build%20App/badge.svg"></a>
    <a href="https://travis-ci.com/openhab/openhab-android"><img alt="Travis CI Status" src="https://travis-ci.com/openhab/openhab-android.svg?branch=master"></a>
    <a href="https://crowdin.com/project/openhab-android"><img alt="Crowdin" src="https://d322cqt584bo4o.cloudfront.net/openhab-android/localized.svg"></a>
    <a href="https://dependabot.com"><img alt="Dependabot Status" src="https://api.dependabot.com/badges/status?host=github&repo=openhab/openhab-android"></a>
    <a href="https://lgtm.com/projects/g/openhab/openhab-android/alerts/"><img alt="Total alerts" src="https://img.shields.io/lgtm/alerts/g/openhab/openhab-android.svg?logo=lgtm&logoWidth=18"></a>
    <a href="https://www.bountysource.com/teams/openhab/issues?tracker_ids=968858"><img alt="Bountysource" src="https://www.bountysource.com/badge/tracker?tracker_id=968858"></a>
    <br>
    <img alt="Logo" src="fastlane/metadata/android/en-US/images/icon.png" width="100">
    <br>
    <b>openHAB client for Android</b>
</p>

## Introduction

This app is a native client for openHAB which allows easy access to your sitemaps.
The documentation is available at [www.openhab.org/docs/](https://www.openhab.org/docs/apps/android.html).

<a href="https://play.google.com/store/apps/details?id=org.openhab.habdroid"><img src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png" alt="Get it on Play Store" height="80"></a>
<a href="https://f-droid.org/app/org.openhab.habdroid"><img src="docs/images/get-it-on-fdroid.png" alt="Get it on F-Droid" height="80"></a>
<a href="https://github.com/openhab/openhab-android/releases"><img src="assets/direct-apk-download.png" alt="Get it on GitHub" height="80"></a>

## Features
* Control your openHAB server and/or [openHAB Cloud instance](https://github.com/openhab/openhab-cloud), e.g., an account with [myopenHAB](http://www.myopenhab.org/)
* Receive notifications through an openHAB Cloud connection, [read more](https://www.openhab.org/docs/configuration/actions.html#cloud-notification-actions)
* Change items via NFC tags
* Send voice commands to openHAB
* [Send alarm clock time to openHAB](https://www.openhab.org/docs/apps/android.html#alarm-clock)
* [Supports wall mounted tablets](https://www.openhab.org/docs/apps/android.html#permanent-deployment)
* [Tasker](https://play.google.com/store/apps/details?id=net.dinglisch.android.taskerm) action plugin included

<img src="docs/images/main-menu.png" alt="Demo Overview" width=200px> <img src="docs/images/widget-overview.png" alt="Widget Overview" width=200px> <img src="docs/images/maps.png" alt="Google Maps Widget" width=200px>

## Beta builds

Beta builds are distributed via [Google Play](https://play.google.com/store/apps/details?id=org.openhab.habdroid.beta), [GitHub](https://github.com/openhab/openhab-android/releases) and [F-Droid](https://f-droid.org/packages/org.openhab.habdroid.beta). Those builds can be installed alongside the stable version.

## Localization

Concerning all `strings.xml` files at [mobile/src/\*/res/values-\*/](mobile/src/main/res/)

All language/regional translations are managed with [Crowdin](https://crowdin.com/).
Please do NOT contribute translations as pull requests against the `mobile/src/*/res/values-*/strings.xml` files directly, but submit them through the Crowdin web service:

- [https://crowdin.com/project/openhab-android](https://crowdin.com/project/openhab-android)

Thanks for your consideration and contribution!

## Setting up development environment

If you want to contribute to Android application we are here to help you to set up development environment. openHAB client for Android is developed using Android Studio.

- Download and install [Android Studio](https://developer.android.com/studio)
- Check out the latest code from GitHub via Android Studio
- Install SDKs and Gradle if you get asked
- Click on "Build Variants" on the left side and change the build variant of the module "mobile" to "fullStableDebug".

You are ready to contribute!

Before producing any amount of code please have a look at [contribution guidelines](CONTRIBUTING.md)

## Build flavors

An optional build flavor "foss" is available for distribution through F-Droid. This build has FCM and crash reporting removed and will not be able to receive push notifications from openHAB Cloud.

For using map view support in the "full" build flavor, you need to visit the [Maps API page](https://developers.google.com/maps/android) and generate an API key via the 'Get a key' button at the top. Then add a line in the following format to the 'gradle.properties' file (either in the same directory as this readme file, or in $HOME/.gradle): `mapsApiKey=<key>`, replacing `<key>` with the API key you just obtained.

## Trademark Disclaimer

Product names, logos, brands and other trademarks referred to within the openHAB website are the property of their respective trademark holders. These trademark holders are not affiliated with openHAB or our website. They do not sponsor or endorse our materials.

Google Play and the Google Play logo are trademarks of Google Inc.
