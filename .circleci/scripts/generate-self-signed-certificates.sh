#!/usr/bin/env bash

SCRIPT_DIR=${REPO}/.circleci/scripts
. $SCRIPT_DIR/rationalize-tf-env-vars.sh

for HOST in ${TF_HOSTS[@]}; do
    CERT_DIR=${REPO}/certificates/$HOST
    mkdir -p $CERT_DIR
    export SAN_IP=$HOST
    openssl req -nodes -x509 -newkey rsa:2048 -days 365 \
        -keyout $CERT_DIR/hedera.key \
        -out $CERT_DIR/hedera.crt \
        -config $SCRIPT_DIR/resources/san-added.cfg \
        -subj "/C=US/ST=TX/L=Richardson/O=Hedera Hashgraph/OU=Engineering/CN=$SAN_IP/emailAddress=admin@hedera.com"
done
