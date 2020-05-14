#!/usr/bin/env bash

. /repo/.circleci/scripts/terraform-functions.sh
. /repo/.circleci/scripts/utils.sh

function firewall_creat_rules {
  for HOST in ${TF_HOSTS[@]}; do
    echo "------- firewall_creat_rules --------- "
    # invoke backgroun script 
    ssh -o StrictHostKeyChecking=no ubuntu@$HOST "nohup ./block_ubuntu.sh &"

    ssh -o StrictHostKeyChecking=no ubuntu@$HOST "sudo ps -ef |grep block "
  done
}

firewall_creat_rules


