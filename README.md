# openHAB Android Client

<img alt="Logo" src="mobile/src/main/res/drawable-xxxhdpi/openhabicon_material.png" width="100">

## Introduction

openHAB Android application is a native client for openHAB. It uses REST API of openHAB to render
sitemaps of your openHAB. It also supports my.openhab.org including push notifications.
Release version of the app is always available for installation through
[Google Play](https://play.google.com/store/apps/details?id=org.openhab.habdroid)
Development snapshots are available for download on [CloudBees](https://openhab.ci.cloudbees.com/job/HABDroid/)

<a href="https://play.google.com/store/apps/details?id=org.openhab.habdroid"><img src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png" height="80"></a>

## Setting up development environment

If you want to contribute to Android application we are here to help you to set up
development environment. openHAB Android app is developed using Android studio and also can be
build with maven.

- Download and install [Android Studio](http://developer.android.com/sdk/installing/studio.html).  Should you feel adventurous, preview versions of Android Studio are also available [here](https://developer.android.com/studio/preview/index.html#linux-canary-bundle).
- After installation, launch Android Studio.  Next, select **Tools** from the main menu, then **Android**, and choose **SDK Manager**.  Install the latest Android build tools, SDKs, and libraries (SDK Tools >= 26.0.0 and SDK Platform-Tools >=26.0.2).
- Download the latest OpenHAB source code from github with the *git* command:  `git clone https://github.com/openhab/openhab.android.git` or download the source from [here](https://github.com/openhab/openhab.android/archive/master.zip).
- Open the project in Android Studio (Select **File** from the main menu, then choose **Open**.  Select the OpenHAB project folder and choose **OK**.)

You are ready to contribute!

Before producing any amount of code please have a look at [contribution guidelines](https://github.com/openhab/openhab.android/blob/master/CONTRIBUTING.md)

## Trademark Disclaimer

Product names, logos, brands and other trademarks referred to within the openHAB website are the
property of their respective trademark holders.  These trademark holders are not affiliated with
openHAB or our website.  They do not sponsor or endorse our materials.

Google Play&trade; and the Google Play&trade; logo are trademarks of Google Inc.
