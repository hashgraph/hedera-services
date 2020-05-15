#!/usr/bin/env bash

set -eE



trap ctrl_c INT

function ctrl_c() {
    echo "** Trapped CTRL-C"
    echo "recover firewall rules"
    sudo iptables --flush
    exit
}



while true; do 
    echo "Block port"
    sudo iptables -A INPUT -p tcp --destination-port 50204 -j DROP
    sleep 8
    echo "Enable port"
    sudo iptables --flush
    sleep 8
done

