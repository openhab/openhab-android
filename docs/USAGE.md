---
layout: documentation
title: Android App
source: https://github.com/openhab/openhab-android/blob/master/docs/USAGE.md
---

{% include base.html %}

<!-- Attention authors: Do not edit directly. Please add your changes to the appropriate source repository -->

# Android App

The openHAB Android application is a native client for openHAB, compatible with phones and tablets.
The app follows the basic principles of the other openHAB UIs, like Basic UI, and presents your predefined openHAB [sitemap(s)](https://www.openhab.org/docs/configuration/sitemaps.html).

<a href="https://play.google.com/store/apps/details?id=org.openhab.habdroid">
  <img alt="Get it on Google Play" src="images/en_badge_web_generic.png" width="240px">
</a>
<a href="https://f-droid.org/app/org.openhab.habdroid">
  <img alt="Get it on F-Droid" src="images/get-it-on-fdroid.png" width="240px">
</a>

## Features

* Control your openHAB server and/or [openHAB Cloud instance](https://github.com/openhab/openhab-cloud), e.g., an account with [myopenHAB](http://www.myopenhab.org/)
* Receive notifications through an openHAB Cloud connection, [read more](https://www.openhab.org/docs/configuration/actions.html#cloud-notification-actions)
* Change items via NFC tags
* Send voice commands to openHAB
* [Send alarm clock time to openHAB](#alarm-clock)
* [Supports wall mounted tablets](#permanent-deployment)
* [Tasker](https://play.google.com/store/apps/details?id=net.dinglisch.android.taskerm) action plugin included

<div class="row">
  <img src="images/main-menu.png" alt="Demo Overview" width=200px> <img src="images/widget-overview.png" alt="Widget Overview" width=200px> <img src="images/maps.png" alt="Google Maps Widget" width=200px>
</div>

## Getting Started

On first start the app tries to discover your openHAB server.
This will only work on local networks and when the server does not enforce either authentication or HTTPS.
If it fails, you can click on `Go to settings` and manually enter the server settings.

The URL field(s) might look like one of the following examples:

* IP address: `http://192.168.1.3:8080`
* Local hostname: `http://openhabianpi:8080` (depending on your network the suffix `.local` needs to be added to the hostname)
* Remote domain name: `https://myopenhab.org` for an openHAB cloud account with [myopenHAB](http://www.myopenhab.org/)

**Local server settings:**
Please enter the base URL of your openHAB server, as you would enter it in the browser to reach the openHAB dashboard.

**Remote server settings:**
If your openHAB instance is reachable via a public address/domain from outside your home network, these settings will be used when the local connection is not successful.
Make sure to secure this connection against unauthorized access.
There are a number of strategies available to provide [secure remote access]({{base}}/installation/security.html) to your openHAB server.

## Permanent Deployment

If you want to use openHAB Android on a wall mounted tablet, go to settings and select `Disable display timer` and `Fullscreen`.

## Alarm Clock

The openHAB app will send the next wake-up time from your alarm clock app to the server.
The time is sent as a number containing the number of milliseconds since the epoch.
The Item name's default is `AlarmClock`, but you can change it in the settings.

Example item definition:
```
Number AlarmClock

```

Example rule:
```
rule "Alarm Trigger"
when
    Time cron "*/10 * * * * ?" // Every 10 seconds
then
    if (AlarmClock.state as Number == 0) {
        // Alarm is turned off
        return;
    }
    val diff = AlarmClock.state as Number - now().millis
    if (diff <= 15000) {
        // Turn on stuff, e.g. radio or light
        logInfo('AlarmLogger', 'Turn on light')
        Light.sendCommand(ON)
    }

end

```

## Help and Technical Details

Please refer to the [openhab-android project on GitHub](https://github.com/openhab/openhab-android) for more details.

### I don't receive any notifications

Please have a look at the "Push notification status" on the About screen in the app.
If it claims that your device is successfully registered at FCM, please open an issue on [openhab-android project on GitHub](https://github.com/openhab/openhab-android) or create a thread in the forum.

### My notifications are delayed

All notifications are sent as "high priority" messages, which means that the device and the openHAB app are waken up and display the notification.
However vendors/third parties can implement custom "cleanup", "optimization" and "battery saver" apps, which might lead to delayed notifications.
Please have a look at [dontkillmyapp.com](https://dontkillmyapp.com/) how to make an exception for openHAB in these apps.

## Trademark Disclaimer

Google Play and the Google Play logo are trademarks of Google Inc.
