---
layout: documentation
title: Android App
source: https://github.com/openhab/openhab-android/blob/master/docs/USAGE.md
---

{% include base.html %}

<!-- Attention authors: Do not edit directly. Please add your changes to the appropriate source repository -->

# Android App

The openHAB Android application is a native client for openHAB, compatible with phones and tablets.
The app follows the basic principles of the other openHAB UIs, like Basic UI, and presents your predefined openHAB [sitemap(s)](http://docs.openhab.org/configuration/sitemaps.html).

<a href="https://play.google.com/store/apps/details?id=org.openhab.habdroid">
  <img alt="Get it on Google Play" src="images/en_badge_web_generic.png" width="240px">
</a>

## Features

* Control your openHAB server and/or [openHAB Cloud instance](https://github.com/openhab/openhab-cloud), e.g., an account with [myopenHAB](http://www.myopenhab.org/)
* Receive notifications through an openHAB Cloud connection, [read more…](http://docs.openhab.org/addons/actions.html)
* Change items via NFC tags
* Send voice commands to openHAB
* Supports wall mounted tablets

<div class="row">
  <div class="col s12 m6"><img src="images/main_menu.png" alt="Demo Overview"></div>
  <div class="col s12 m6"><img src="images/widget_overview.png" alt="Demo Widget Overview"></div>
</div>

## Getting Started

When first installed the app is in "Demo Mode".
To connect to your own openHAB server, first navigate into the Settings menu and uncheck the "Demo Mode" option.
Normally, after unchecking the Demo Mode, the app will be able to use multicast DNS to auto-detect your openHAB server if it is on the same network.

You also have the option to manually set the server URL in the settings.

**Local server settings:**
Please enter the base URL to your openHAB server as you would enter it in the browser to reach the openHAB dashboard.

**Remote server settings:**
If you want to use openHAB cloud, please enter URL, user and password of you cloud instance here.
Please make sure to [secure this connection](http://docs.openhab.org/installation/security.html).

The URL might look like one of the following examples:

* IP address: `http://192.168.1.3:8080`
* Local DNS name: `http://openhabianpi:8080` respectively `http://openhabianpi.local:8080` (depending on your network)

Once the URL is set correctly, the display of the app will be determined by the sitemaps defined on your server.

The option to set a "Remote URL" allows the app to be used when you are away from home.
There are a number of strategies available to provide [secure remote access]({{base}}/installation/security.html) to your openHAB server.

## Permanent Deployment

If you want to use openHAB Android on a wall mounted tablet, go to settings and tick `Disable display timer` and `Fullscreen`.

## Help and Technical Details

Please refer to the [openhab-android project on GitHub](https://github.com/openhab/openhab-android) for more details.

## Trademark Disclaimer

Google Play and the Google Play logo are trademarks of Google Inc.
