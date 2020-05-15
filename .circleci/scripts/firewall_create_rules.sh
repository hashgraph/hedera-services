#!/usr/bin/env bash

. /repo/.circleci/scripts/terraform-functions.sh
. /repo/.circleci/scripts/utils.sh

function firewall_creat_rules {

    # running block script at the last node
    HOST=${TF_HOSTS[${#TF_HOSTS[@]}-1]}

    echo "------- firewall_creat_rules $HOST --------- "

    #copy script to remote instances
    scp -p -o StrictHostKeyChecking=no ${REPO}/.circleci/scripts/block_ubuntu.sh ubuntu@$HOST:$HAPI_APP_DIR/
    ssh -o StrictHostKeyChecking=no ubuntu@$HOST "cd $HAPI_APP_DIR; ls -ltr"

    # invoke backgroun script 
    ssh -o StrictHostKeyChecking=no ubuntu@$HOST "cd $HAPI_APP_DIR; nohup ./block_ubuntu.sh &" &

    sleep 2
    ssh -o StrictHostKeyChecking=no ubuntu@$HOST "sudo ps -ef |grep block "
    
    # only run block port on the first node
    break
}

firewall_creat_rules


