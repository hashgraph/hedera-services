#!/usr/bin/env bash
projectName="$1"
timestamp=`TZ=UTC date -d '6 hours ago' "+%Y-%m-%dT%H:%M:%S"`
echo "Project Name : $projectName"
echo "Instances before $timestamp will be deleted"
list=`gcloud compute instance-groups managed list --filter="(name~.*gcp-daily.* OR name~.*gcp-ondemand.* OR name~.*gcp-weekly.* OR name~.*gcp-commit-.*) AND creationTimestamp<$timestamp" --format="value(name,zone.scope())" --project=$projectName`
echo "Instances to be deleted : $list"
while IFS= read -r line
do
    values=( $line )
    name="${values[0]}"
    zone="${values[1]}"
    gcloud compute instance-groups managed delete $name --project=$projectName  --zone=$zone --quiet --format text
done <<< "$list"

echo "Finished deleting all Instances"