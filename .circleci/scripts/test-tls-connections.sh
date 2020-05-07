#!/usr/bin/env bash

. ${REPO}/.circleci/scripts/rationalize-tf-env-vars.sh

for HOST in ${TF_HOSTS[@]}; do
    sudo openssl s_client -showcerts -host $HOST -port 50212
done
