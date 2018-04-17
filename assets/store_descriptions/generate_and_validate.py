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
    return(enRoot.findall(key)[0].text)

def getString(key):
    try:
        string = root.findall(key)[0].text
        if emptyStringPattern.match(string):
            string = getEnglishString(key)
    except:
        string = getEnglishString(key)
    return(string)

stringsFiles=glob.glob('assets/store_descriptions/*/strings.xml')
for file in stringsFiles:
    tree = ET.parse(file)
    root = tree.getroot()
    lang = file[26:-12]

    fullDescription = getString('intro') + "\n\n"
    if sys.argv[1] == "fdroid":
        fullDescription+= getString('fdroid') + "\n\n"
    elif sys.argv[1] == "fdroidBeta":
        fullDescription += getString('fdroid') + "\n"
        fullDescription += getString('fdroid_beta') + "\n\n"
    fullDescription += "<b>" + getString('important_note') + "</b>\n\n"
    fullDescription += getString('oh_server') + "\n\n"
    fullDescription += getString('whatis') + "\n"
    fullDescription += getString('rules') + "\n\n"
    fullDescription += "<b>" + getString('supported') + "</b>\n\n"
    fullDescription += getString('bindings') + "\n\n"
    fullDescription += getString('home_automation_solutions') + "\n"
    fullDescription += getString('lighting') + "\n"
    fullDescription += getString('heating') + "\n"
    fullDescription += getString('home_entertainment') + "\n"
    fullDescription += getString('security') + "\n"
    fullDescription += getString('open_protocols') + "\n"
    fullDescription += getString('special_useCases') + "\n"
    fullDescription += getString('empty_point') + "\n\n"
    fullDescription += "<b>" + getString('oss_community') + "</b>\n\n"
    fullDescription += getString('forum') + "\n"
    fullDescription += getString('report_issues') + "\n"
    fullDescription += getString('translation') + "\n\n"
    fullDescription += "<b>" + getString('foundation') + "</b>\n\n"
    fullDescription += getString('about_foundation') + "\n"

    if len(fullDescription) > 4000:
        print("Description of " + lang + " too long!")
        exitCode += 1

    shortDescription = getString('short_description')
    if len(shortDescription) > 80:
        print("Description of " + lang + " too long!")
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

exit(exitCode)
