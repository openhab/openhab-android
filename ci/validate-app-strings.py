#!/usr/bin/env python3

import glob
import re

exitCode = 0

def checkStrings(flavor):
    errorCount = 0
    print("Checking " + flavor + " app strings...")
    appStringsFiles = glob.glob('mobile/src/' + flavor + '/res/values-*/strings.xml')
    for file in appStringsFiles:
        lang = file[27:-12]
        print("Processing " + lang)
        strings = open(file, "r").read()

        openhabOccurences = [m.start() for m in re.finditer("openhab", strings, re.I)]
        for i in openhabOccurences:
            openhabString = strings[i:i+7]
            if openhabString != "openhab" and openhabString != "openHAB": # "openhab" is used in links
                print("Incorrect spelling of openHAB")
                errorCount += 1
    if len(appStringsFiles) == 0:
        print("No files to validate")
    print("\n\n")
    return errorCount

exitCode += checkStrings("main")
exitCode += checkStrings("full")
exitCode += checkStrings("foss")
exitCode += checkStrings("beta")
exitCode += checkStrings("stable")

exit(exitCode)
