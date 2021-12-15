#!/usr/bin/env bash
projectName="$1"
timestamp=`date +"%Y-%m-%dT%T"`
echo "$projectName"
echo "$timestamp"
list=`gcloud compute instance-groups managed list --filter="(name~.*gcp-daily.* OR name~.*gcp-ondemand.* OR name~.*gcp-weekly.* OR name~.*gcp-commit.*) AND creationTimestamp<2021-12-01T04:26:22" --format="value(name,zone.scope())" --project=$projectName`
echo "$list"
while IFS= read -r line
do
    values=( $line )
    name="${values[0]}"
    zone="${values[1]}"
    gcloud compute instance-groups managed delete $name --project=$projectName  --zone=$zone --quiet --format text
done <<< "$list"