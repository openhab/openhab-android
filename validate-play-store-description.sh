#!/bin/bash

# Checks if play store descriptions does not exceed char limits
# https://support.google.com/googleplay/android-developer/answer/113469?hl=en

cd fastlane/metadata/android

error=0

for folder in *
do
    cd "$folder"
    if [ -f "full_description.txt" ]
    then
        chars=$(wc -m full_description.txt | cut -d " " -f 1)
        if [ "$chars" -gt 4000 ]
        then
            echo "Full descriptions in $folder exceeds 4000 char limit"
            let error++
        fi
    fi
    if [ -f "short_description.txt" ]
    then
        chars=$(wc -m short_description.txt | cut -d " " -f 1)
        if [ "$chars" -gt 80 ]
        then
            echo "Short descriptions in $folder exceeds 80 char limit"
            let error++
        fi
    fi
    cd ..
done

if [ "$error" -ne 0 ]
then
    exit 1
fi
