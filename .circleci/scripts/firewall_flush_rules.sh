#!/usr/bin/env bash

. /repo/.circleci/scripts/terraform-functions.sh
. /repo/.circleci/scripts/utils.sh

function firewall_flush_rules {
  for HOST in ${TF_HOSTS[@]}; do
    echo "------- flush_firewall_rules = --------- "
    #kill background script
    ssh -o StrictHostKeyChecking=no ubuntu@$HOST "sudo pkill -f block_ubuntu.sh"

    # make sure it being killed
    ssh -o StrictHostKeyChecking=no ubuntu@$HOST "sudo ps -ef |grep block "

    #reset firewall rules
    ssh -o StrictHostKeyChecking=no ubuntu@$HOST "sudo iptables --flush"
  done
}

firewall_flush_rules


