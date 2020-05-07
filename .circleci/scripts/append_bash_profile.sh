#!/usr/bin/env bash

. /repo/.circleci/scripts/terraform-functions.sh
. /repo/.circleci/scripts/utils.sh

function append_bash_profile {
  for HOST in ${TF_HOSTS[@]}; do
    echo "------- cat ~/.bash_profile  --------- "
    ssh -o StrictHostKeyChecking=no ubuntu@$HOST "echo $1 >> ~/.bash_profile"
    ssh -o StrictHostKeyChecking=no ubuntu@$HOST "cat ~/.bash_profile"
  done
}

append_bash_profile "export CI_AWS=launch_by_ci"



