<p align="center">
  <a href="https://travis-ci.org/openhab/openhab-android">
    <img src="https://travis-ci.org/openhab/openhab-android.svg?branch=master">
  </a>
  <a href="https://crowdin.com/project/openhab-android">
    <img src="https://d322cqt584bo4o.cloudfront.net/openhab-android/localized.svg">
  </a>
    <a href="https://codecov.io/gh/openhab/openhab-android/branch/master">
    <img src="https://codecov.io/gh/openhab/openhab-android/branch/master/graph/badge.svg">
  </a>
  <a href="https://www.bountysource.com/teams/openhab/issues?tracker_ids=968858">
    <img src="https://www.bountysource.com/badge/tracker?tracker_id=968858">
  </a>
  <br>
  <img alt="Logo" src="fastlane/metadata/android/en-US/images/icon.png" width="100">
  <br>
  <b>openHAB Android Client</b>
</p>

## Introduction

openHAB Android application is a native client for openHAB. It uses REST API of openHAB to render sitemaps of your openHAB.

<a href="https://play.google.com/store/apps/details?id=org.openhab.habdroid"><img src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png" height="80"></a>

## Features
* Control your openHAB server and [openHAB Cloud instance](https://github.com/openhab/openhab-cloud)
* Receive notifications from openHAB Cloud
* Change items via NFC tags
* Send voice commands to openHAB
* Discover devices and add them as items
* Supports wall mounted tablets

<img src="https://raw.githubusercontent.com/openhab/openhab.android/master/fastlane/metadata/android/en-US/phoneScreenshots/main_menu.png" width="200px"> <img src="https://raw.githubusercontent.com/openhab/openhab.android/master/fastlane/metadata/android/en-US/phoneScreenshots/widget_overview.png" width="200px"> <img src="https://raw.githubusercontent.com/openhab/openhab.android/master/fastlane/metadata/android/en-US/phoneScreenshots/astro_binding.png" width="200px">

## Localization

Concerning all `values-*` folders at [mobile/src/main/res](mobile/src/main/res/)

All language/regional translations are managed with [Crowdin](https://crowdin.com/).
Please do NOT contribute translations as pull requests against the `mobile/src/main/res/values-*` folders directly, but submit them through the Crowdin web service:

- [https://crowdin.com/project/openhab-android](https://crowdin.com/project/openhab-android)

Thanks for your consideration and contribution!

## Setting up development environment

If you want to contribute to Android application we are here to help you to set up development environment. openHAB Android app is developed using Android Studio and also can be build with maven.

- Download and install [Android Studio](https://developer.android.com/sdk/installing/studio.html) Android -> SDK Manager
- Check out the latest code from Github via Android Studio
- Install SDKs and Gradle if you get asked
- Click on "Build Variants" on the left side and change the build variant of the module "mobile" to fullDebug

You are ready to contribute!

Before producing any amount of code please have a look at [contribution guidelines](https://github.com/openhab/openhab.android/blob/master/CONTRIBUTING.md)

## Build flavors

An optional build flavor "foss" is available for distribution through F-Droid. This build has GCM and crash reporting removed and will not be able to receive push notifications from openHAB Cloud.

## Trademark Disclaimer

Product names, logos, brands and other trademarks referred to within the openHAB website are the property of their respective trademark holders. These trademark holders are not affiliated with openHAB or our website. They do not sponsor or endorse our materials.

Google Play and the Google Play logo are trademarks of Google Inc.
