#!/usr/bin/env python3

import xml.etree.ElementTree as ET
import glob
import sys


en_tree = ET.parse('assets/store_descriptions/en-US/strings.xml')
en_root = en_tree.getroot()

def getString(key):
    try:
        string = root.findall(key)[0].text
    except:
        string = en_root.findall(key)[0].text
    return(string)

strings_files=glob.glob('assets/store_descriptions/*/strings.xml')
for file in strings_files:
    tree = ET.parse(file)
    root = tree.getroot()
    lang=file[26:-12]

    full_description = getString('intro') + "\n\n"
    if sys.argv[1] == "fdroid":
        full_description+= getString('fdroid') + "\n\n"
    elif sys.argv[1] == "fdroidBeta":
        full_description+= getString('fdroid') + "\n"
        full_description+= getString('fdroid_beta') + "\n\n"
    full_description+= "<b>" + getString('important_note') + "</b>\n\n"
    full_description+= getString('oh_server') + "\n\n"
    full_description+= getString('whatis') + "\n"
    full_description+= getString('rules') + "\n\n"
    full_description+="<b>" + getString('supported') + "</b>\n\n"
    full_description+= getString('bindings') + "\n\n"
    full_description+= getString('home_automation_solutions') + "\n"
    full_description+= getString('lighting') + "\n"
    full_description+= getString('heating') + "\n"
    full_description+= getString('home_entertainment') + "\n"
    full_description+= getString('security') + "\n"
    full_description+= getString('open_protocols') + "\n"
    full_description+= getString('special_useCases') + "\n"
    full_description+= getString('empty_point') + "\n\n"
    full_description+= "<b>" + getString('oss_community') + "</b>\n\n"
    full_description+= getString('forum') + "\n"
    full_description+= getString('report_issues') + "\n"
    full_description+= getString('translation') + "\n\n"
    full_description+= "<b>" + getString('foundation') + "</b>\n\n"
    full_description+= getString('about_foundation') + "\n"

    if len(full_description) > 4000:
        print("Description of " + lang + " too long!")


    short_description = getString('short_description')
    if len(short_description) > 80:
        print("Description of " + lang + " too long!")

    f = open('fastlane/metadata/android/' + lang + '/full_description.txt', 'w')
    f.write(full_description)
    f.close()
    f = open('fastlane/metadata/android/' + lang + '/short_description.txt', 'w')
    f.write(short_description)
    f.close()

