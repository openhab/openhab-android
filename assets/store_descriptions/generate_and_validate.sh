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
store_string_base="assets/store_descriptions"
app_string_base="mobile/src/main/res"
resource_base="fastlane/metadata/android"

for folder in ${store_string_base}/*
do
    [ ! -d "$folder" ] && continue
    if [ -f "${folder}/strings.sh" ]
    then
        lang=${folder#${store_string_base}/}
        echo "Validate and generate $lang store description"
        if egrep '\||;|http:|\\' "${folder}/strings.sh" --color=always || [ $(grep '#' "${folder}/strings.sh" | wc -l) -gt 2 ]
        then
            echo "Prohibited char found in $lang store description, exiting" 1>&2
            exit 1
        fi
        source "${store_string_base}/en-US/strings.sh"
        source "${folder}/strings.sh"
        if [ ! -d "${resource_base}/${lang}" ]
        then
            mkdir "${resource_base}/${lang}"
        fi
        sed -e "s|\$intro|$intro|" \
            -e "s|\$whatis|$whatis|" \
            -e "s|\$rules|$rules|" \
            -e "s|\$supported|$supported|" \
            -e "s|\$bindings|$bindings|" \
            -e "s|\$home_automation_solutions|$home_automation_solutions|" \
            -e "s|\$lighting|$lighting|" \
            -e "s|\$heating|$heating|" \
            -e "s|\$home_entertainment|$home_entertainment|" \
            -e "s|\$security|$security|" \
            -e "s|\$open_protocols|$open_protocols|" \
            -e "s|\$special_useCases|$special_useCases|" \
            -e "s|\$oss_community|$oss_community|" \
            -e "s|\$forum|$forum|" \
            -e "s|\$report_issues|$report_issues|" \
            -e "s|\$translation|$translation|" \
            -e "s|\$foundation|$foundation|" \
            -e "s|\$about_foundation|$about_foundation|" \
            -e "s|\$important_note|$important_note|" \
            -e "s|\$oh_server|$oh_server|" \
            "$full_description_template" > "${resource_base}/${lang}/full_description.txt"
        echo $short_description > "${resource_base}/${lang}/short_description.txt"

        # Validation
        chars=$(wc -m "${resource_base}/${lang}/full_description.txt" | cut -d " " -f 1)
        if [ "$chars" -gt 4000 ]
        then
            echo "Full descriptions exceeds 4000 char limit" 1>&2
            let error++ || true
        fi
        chars=$(wc -m "${resource_base}/${lang}/short_description.txt" | cut -d " " -f 1)
        if [ "$chars" -gt 80 ]
        then
            echo "Short descriptions exceeds 80 char limit" 1>&2
            let error++ || true
        fi
    fi
done


for folder in ${app_string_base}/values-*
do
    [ ! -d "$folder" ] && continue
    if [ -f "${folder}/strings.xml" ]
    then
        lang=${folder#${app_string_base}/values-}
        echo "Validate $lang app strings"
        if egrep 'HTTP response code' "${folder}/strings.xml" --color=always
        then
            echo "Prohibited chars found in $lang app strings" 1>&2
            let error++ || true
        fi
    fi
done


if [ "$error" -eq 0 ]
then
    echo "All translations are valid!"
else
    echo "$error errors occured while validating translations" 1>&2
    exit 1
fi
