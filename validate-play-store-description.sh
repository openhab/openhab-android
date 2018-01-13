#!/bin/bash

# Checks if play store descriptions does not exceed char limits
# https://support.google.com/googleplay/android-developer/answer/113469?hl=en

error=0

for folder in fastlane/metadata/android/*
do
    [ ! -d "$folder" ] && continue
    if [ -f "${folder}/full_description.txt" ]
    then
        chars=$(wc -m "${folder}/full_description.txt" | cut -d " " -f 1)
        if [ "$chars" -gt 4000 ]
        then
            echo "Full descriptions in $folder exceeds 4000 char limit"
            let error++
        fi
    fi
    if [ -f "${folder}/short_description.txt" ]
    then
        chars=$(wc -m "${folder}/short_description.txt" | cut -d " " -f 1)
        if [ "$chars" -gt 80 ]
        then
            echo "Short descriptions in $folder exceeds 80 char limit"
            let error++
        fi
    fi
done

if [ "$error" -eq 0 ]
then
    echo "All Play Store descriptions are valid!"
else
    echo "$error errors occured when validation Play Store descriptions!" 1>&2
    exit 1
fi
