#!/bin/bash

set -xv

# Checks if play store descriptions does not exceed char limits
# https://support.google.com/googleplay/android-developer/answer/113469?hl=en

error=0

full_description_template="assets/store_descriptions/full-description.txt.template"
string_base="assets/store_descriptions/"
resource_base="fastlane/metadata/android/"

for folder in $string_base*
do
    [ ! -d "$folder" ] && continue
    if [ -f "${folder}/strings.lng" ]
    then
        source "${folder}/strings.lng"
        if [[ -z $intro || -z $whatis || -z $rules || -z $supported || -z $bindings || -z $home_automation_solutions || -z $lighting || -z $heating || -z $home_entertainment || -z $security || -z $open_protocols || -z $special_useCases || -z $oss_community || -z $forum || -z $report_issues || -z $translation || -z $foundation || -z $about_foundation || -z $important_note || -z $oh_server || -z $short_description ]]
        then
            echo "At least one variable is not set"
            continue
        fi
        lang=${folder#$string_base}
        echo $lang
        envsubst < "$full_description_template" > "${resource_base}${lang}/full_description.txt"
        echo $short_description > "${resource_base}${lang}/short_description.txt"

        # Validation
        chars=$(wc -m "${resource_base}${lang}/full_description.txt" | cut -d " " -f 1)
        if [ "$chars" -gt 4000 ]
        then
            echo "Full descriptions in $folder exceeds 4000 char limit"
            let error++
        fi
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
    echo "$error errors occured when validating Play Store descriptions!" 1>&2
    exit 1
fi
