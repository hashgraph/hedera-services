#!/usr/bin/env bash

. /repo/.circleci/scripts/terraform-functions.sh
. /repo/.circleci/scripts/utils.sh


function firewall_create_rules {

    # running block script at the last node
    HOST=$1

    echo "------- firewall_creat_rules $HOST --------- "

    #copy script to remote instances
    scp -p -o StrictHostKeyChecking=no ${REPO}/.circleci/scripts/block_ubuntu.sh ubuntu@$HOST:$HAPI_APP_DIR/

    # invoke backgroun script 
    ssh -o StrictHostKeyChecking=no ubuntu@$HOST "cd $HAPI_APP_DIR; nohup ./block_ubuntu.sh &" &

    sleep 2

    # check if the block script is running
    ssh -o StrictHostKeyChecking=no ubuntu@$HOST "sudo ps -ef |grep block | grep -v grep"


}

function packet_loss {
  HOST=$1
  echo "Packet loss on node $HOST"
  ssh -o StrictHostKeyChecking=no ubuntu@$HOST "sudo tc qdisc add dev ens3 root netem loss 0.1% 25% "

}


function packet_corruption  {
  HOST=$1
  echo "Packet loss on node $HOST"
  ssh -o StrictHostKeyChecking=no ubuntu@$HOST "sudo tc qdisc add dev ens3 root netem corrupt 0.1% "

}

function packet_reorder {
  HOST=$1
  echo "Packet reorder on node $HOST"
  ssh -o StrictHostKeyChecking=no ubuntu@$HOST "sudo tc qdisc add dev ens3 root netem reorder 0.01% gap 5000 delay 10ms "

}

# the first node drop packet
packet_loss ${TF_HOSTS[0]}

# the second node delay packet
packet_corruption ${TF_HOSTS[1]}

# the third node randome corrupt packet
packet_reorder ${TF_HOSTS[2]}

# the last node block network
firewall_create_rules ${TF_HOSTS[${#TF_HOSTS[@]}-1]}
