#!/usr/bin/env bash

. ${REPO}/.circleci/scripts/terraform-functions.sh
. ${REPO}/.circleci/scripts/utils.sh

function show_java_process {
  for HOST in ${TF_HOSTS[@]}; do
    echo "-------show_java_process --------- "
    ssh -o StrictHostKeyChecking=no ubuntu@$HOST "cd $HAPI_APP_DIR; ls -ltr"
    ssh -o StrictHostKeyChecking=no ubuntu@$HOST "sudo ps -ef |grep java"
  done
}

show_java_process
