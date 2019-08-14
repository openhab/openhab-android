#!/usr/bin/env python3

import glob
import os
import re
import sys
import xml.etree.ElementTree as ET

enTree = ET.parse('assets/store_descriptions/en-US/strings.xml')
enRoot = enTree.getroot()

emptyStringPattern = re.compile("^([ ]+)+$")

exitCode = 0

def getEnglishString(key):
    return enRoot.findall(key)[0].text

def getString(key):
    try:
        string = root.findall(key)[0].text
        if emptyStringPattern.match(string):
            string = getEnglishString(key)
    except (TypeError, IndexError):
        string = getEnglishString(key)
    return string

playDevSiteDescription = "Play Store developer site description:\n"

print("Checking app store strings...")
appStoreStringsFiles = glob.glob('assets/store_descriptions/*/strings.xml')
for file in appStoreStringsFiles:
    tree = ET.parse(file)
    root = tree.getroot()
    lang = file[26:-12]
    print("Processing " + lang)

    fullDescription = getString('intro') + "\n\n"
    if sys.argv[1] == "fdroid":
        fullDescription += getString('fdroid') + "\n\n"
    elif sys.argv[1] == "fdroidBeta":
        fullDescription += getString('fdroid') + "\n"
        fullDescription += getString('beta') + "\n"
        fullDescription += getString('fdroid_beta') + "\n\n"
    elif sys.argv[1] == "playBeta":
        fullDescription += getString('beta') + "\n"
        fullDescription += getString('play_beta') + "\n\n"
    fullDescription += getString('whatis') + "\n"
    fullDescription += getString('rules') + "\n\n"
    fullDescription += "<b>" + getString('supported') + "</b>\n\n"
    fullDescription += getString('bindings') + "\n"
    fullDescription += getString('automation_apps') + "\n\n"
    fullDescription += "<b>" + getString('oss_community') + "</b>\n\n"
    fullDescription += getString('forum') + "\n"
    fullDescription += getString('report_issues') + "\n"
    fullDescription += getString('translation') + "\n\n"
    fullDescription += "<b>" + getString('foundation') + "</b>\n\n"
    fullDescription += getString('about_foundation') + "\n\n"
    fullDescription += "<b>" + getString('important_note') + "</b>\n\n"
    fullDescription += getString('oh_server')
    if "fdroid" in sys.argv[1]:
        fullDescription += "\n\n" + getString('fdroid_privacy_policy')

    # Validate full description
    openhabOccurences = [m.start() for m in re.finditer("openhab", fullDescription, re.I)]
    for i in openhabOccurences:
        openhabString = fullDescription[i:i+7]
        if openhabString != "openhab" and openhabString != "openHAB": # "openhab" is used in links
            print("Incorrect spelling of openHAB in " + lang)
            exitCode += 1

    if "http://" in fullDescription:
        print("HTTP link found in " + lang)
        exitCode += 1

    if '<a href="https://f-droid.org/packages/org.openhab.habdroid/">' not in getString('fdroid_beta') or "</a>" not in getString('fdroid_beta'):
        print("Missing tags in 'fdroid_beta' of " + lang)
        exitCode += 1

    if len(fullDescription) > 4000:
        print("Full description of " + lang + " is too long: " + str(len(fullDescription)) + " > 4000 chars")
        exitCode += 1

    # Validate short description
    shortDescription = getString('short_description')
    if len(shortDescription) > 80:
        print("Short description of " + lang + " is too long: " + str(len(shortDescription)) + " > 80 chars")
        exitCode += 1

    newpath = r'fastlane/metadata/android/' + lang + '/'
    if not os.path.exists(newpath):
            os.makedirs(newpath)

    f = open('fastlane/metadata/android/' + lang + '/full_description.txt', 'w')
    f.write(fullDescription)
    f.close()
    f = open('fastlane/metadata/android/' + lang + '/short_description.txt', 'w')
    f.write(shortDescription)
    f.close()

    intro = getString('intro')
    if intro != getEnglishString('intro'):
        playDevSiteDescription += lang + ": " + intro + "\n"
        if len(intro) > 140:
            print("Intro string of " + lang + " is too long: " + str(len(getString('intro'))) + " > 140 chars")
            exitCode += 1

print("\n\n" + playDevSiteDescription)

exit(exitCode)
