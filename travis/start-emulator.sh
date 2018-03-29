#!/bin/bash

if [ -z "$1" ]
then
    echo "First argument must be the device"
    exit 1
fi

if [[ "$1" == "phone" ]]
then
    device="5.4in FWVGA"
elif [[ "$1" == "sevenInch" ]]
then
    device="7in WSVGA (Tablet)"
elif [[ "$1" == "tenInch" ]]
then
    device="10.1in WXGA (Tablet)"
fi

echo "Stop old emulator"
time adb devices | grep emulator | cut -f1 | while read line; do adb -s $line emu kill; done

echo "Start new $device emulator"
echo no | android create avd --force -n emulator -t "android-22" --abi armeabi-v7a -d "$device"
emulator -avd emulator -no-audio &
android-wait-for-emulator
adb shell input keyevent 82 &
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command battery -e plugged false -e level 100
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
adb shell am broadcast -a com.android.systemui.demo -e command status -e location hide -e alarm hide -e volume hide
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0815
adb shell am broadcast -a com.android.systemui.demo -e command network -e mobile show -e datatype 4g -e level 4 -e wifi show -e fully true -e level 3
sed -i -e /device_type/d fastlane/Screengrabfile
echo "device_type('$1')" >> fastlane/Screengrabfile
