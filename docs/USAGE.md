---
layout: documentation
title: Android openHAB App
---

{% include base.html %}

# Android openHAB App

openHAB Android application is a native client for openHAB. It uses REST API of openHAB to render your sitemaps.

<a href="https://play.google.com/store/apps/details?id=org.openhab.habdroid"><img src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png" height="80"></a>

## Features
* Control your openHAB server and [openHAB Cloud instance](https://github.com/openhab/openhab-cloud)
* Receive notifications from openHAB Cloud
* Change items via NFC tags
* Send voice commands to openHAB
* Discover devices and add them as items
* Supports wall mounted tablets

<img src="images/main_menu.png" width="200px"> <img src="images/widget_overview.png" width="200px">

## Getting Started

When first installed the app is in "Demo Mode".
To connect it to your own openHAB server, first navigate to Settings and uncheck the "Demo Mode" option.
Normally, after unchecking the Demo Mode, the app will be able to use multicast DNS to autodetect to your openHAB server if it is on the same network.

You also have the option to manually set the server URL in the settings.
Please enter the base URL to your openHAB server as you would enter it in the browser to reach the openHAB dashboard.
The URL might look like one of the following examples.

* IP address: `http://192.168.1.3:8080`
* Local DNS name: `http://openhabianpi:8080` respectively `http://openhabianpi.local:8080` (depending on your network)

Once the URL is set correctly, the display of the app will be determined by the sitemaps defined on your server.

The option to set a "Remote URL" allows the app to be used when you are away from home.
There are a number of strategies available to provide [secure remote access]({{base}}/installation/security.html) to your openHAB server.

## Help and technical details

Please refer to the [openhab/android project on GitHub](https://github.com/openhab/openhab.android) for more details.

## Trademark Disclaimer

Product names, logos, brands and other trademarks referred to within the openHAB website are the property of their respective trademark holders. These trademark holders are not affiliated with openHAB or our website. They do not sponsor or endorse our materials.

Google Play and the Google Play logo are trademarks of Google Inc.
