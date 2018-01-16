#!/bin/bash

# Exit on error
set -e

# Checks if play store descriptions does not exceed char limits
# https://support.google.com/googleplay/android-developer/answer/113469?hl=en

# Check if called from repository root
[ -d "fastlane/metadata/android" ] || exit 1

# Remove old descriptions
find fastlane/metadata/android/ -name "*_description.txt" -delete

error=0

full_description_template="assets/store_descriptions/full-description.txt.template"
string_base="assets/store_descriptions"
resource_base="fastlane/metadata/android"

for folder in ${string_base}/*
do
    [ ! -d "$folder" ] && continue
    if [ -f "${folder}/strings.sh" ]
    then
        source "${string_base}/en-US/strings.sh"
        source "${folder}/strings.sh"
        lang=${folder#${string_base}/}
        if [ ! -d "${resource_base}/${lang}" ]
        then
            mkdir "${resource_base}/${lang}"
        fi
        echo "Generate $lang"
        sed -e "s/\$intro/$intro/" \
            -e "s/\$whatis/$whatis/" \
            -e "s/\$rules/$rules/" \
            -e "s/\$supported/$supported/" \
            -e "s/\$bindings/$bindings/" \
            -e "s/\$home_automation_solutions/$home_automation_solutions/" \
            -e "s/\$lighting/$lighting/" \
            -e "s/\$heating/$heating/" \
            -e "s/\$home_entertainment/$home_entertainment/" \
            -e "s/\$security/$security/" \
            -e "s/\$open_protocols/$open_protocols/" \
            -e "s/\$special_useCases/$special_useCases/" \
            -e "s/\$oss_community/$oss_community/" \
            -e "s/\$forum/$forum/" \
            -e "s/\$report_issues/$report_issues/" \
            -e "s/\$translation/$translation/" \
            -e "s/\$foundation/$foundation/" \
            -e "s/\$about_foundation/$about_foundation/" \
            -e "s/\$important_note/$important_note/" \
            -e "s/\$oh_server/$oh_server/" \
            "$full_description_template" > "${resource_base}/${lang}/full_description.txt"
        echo $short_description > "${resource_base}/${lang}/short_description.txt"

        # Validation
        chars=$(wc -m "${resource_base}/${lang}/full_description.txt" | cut -d " " -f 1)
        if [ "$chars" -gt 4000 ]
        then
            echo "Full descriptions exceeds 4000 char limit"
            let error++
        fi
        chars=$(wc -m "${resource_base}/${lang}/short_description.txt" | cut -d " " -f 1)
        if [ "$chars" -gt 80 ]
        then
            echo "Short descriptions exceeds 80 char limit"
            let error++
        fi
    fi
done

if [ "$error" -eq 0 ]
then
    echo "All Play Store descriptions are valid!"
else
    echo "$error errors occured when validating Play Store descriptions!" 1>&2
    exit 1
fi
