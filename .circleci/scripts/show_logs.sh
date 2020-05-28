#!/usr/bin/env bash

. ${REPO}/.circleci/scripts/terraform-functions.sh
. ${REPO}/.circleci/scripts/utils.sh

function print_downloaded_hosts_output {
  for HOST in ${TF_HOSTS[@]}; do
    echo "------------------ swirlds.log ------------------"
    cat ${REPO}/HapiApp2.0/$HOST/output/swirlds.log
    echo "------------------ hgcaa.log ------------------"
    cat ${REPO}/HapiApp2.0/$HOST/output/hgcaa.log
    echo "------------------ exec.log ------------------"
    if [[ -f ${REPO}/HapiApp2.0/$HOST/exec.log ]]; then
        cat  ${REPO}/HapiApp2.0/$HOST/exec.log
    fi
  done
}

download_node_output
print_downloaded_hosts_output
