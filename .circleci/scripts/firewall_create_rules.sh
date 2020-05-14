#!/usr/bin/env bash

. /repo/.circleci/scripts/terraform-functions.sh
. /repo/.circleci/scripts/utils.sh

function firewall_creat_rules {
  for HOST in ${TF_HOSTS[@]}; do
    echo "------- firewall_creat_rules --------- "

    #copy script to remote instances
    scp -p -o StrictHostKeyChecking=no ${REPO}/.circleci/scripts/block_ubuntu.sh ubuntu@$HOST:$HAPI_APP_DIR/
    ssh -o StrictHostKeyChecking=no ubuntu@$HOST "cd $HAPI_APP_DIR; ls -ltr"

    # invoke backgroun script 
    ssh -o StrictHostKeyChecking=no ubuntu@$HOST "nohup ./block_ubuntu.sh &"
    sleep 5
    ssh -o StrictHostKeyChecking=no ubuntu@$HOST "sudo ps -ef |grep block "
  done
}

firewall_creat_rules


