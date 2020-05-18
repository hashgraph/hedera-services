#!/usr/bin/env bash

. /repo/.circleci/scripts/terraform-functions.sh
. /repo/.circleci/scripts/utils.sh

function print_downloaded_hosts_output {
  for HOST in ${TF_HOSTS[@]}; do
    echo "------------------ swirlds.log ------------------"
    cat /repo/HapiApp2.0/$HOST/output/swirlds.log
    echo "------------------ hgcaa.log ------------------"
    cat /repo/HapiApp2.0/$HOST/output/hgcaa.log
    echo "------------------ exec.log ------------------"
    if [[ -f /repo/HapiApp2.0/$HOST/exec.log ]]; then
        cat  /repo/HapiApp2.0/$HOST/exec.log
    fi 
    echo "------------------ csv ------------------"
    cat /repo/HapiApp2.0/$HOST/*.csv      
  done
}

download_node_output
print_downloaded_hosts_output


