#!/usr/bin/env bash

. ${REPO}/.circleci/scripts/rationalize-tf-env-vars.sh

for I in "${!TF_HOSTS[@]}"; do
    sudo keytool -import -trustcacerts -noprompt \
        -file "${REPO}/certificates/${TF_HOSTS[$I]}/hedera.crt" -alias "HederaCert$I" \
        -cacerts -storepass $CACERTS_STORE_PASS
    sudo keytool -list -v -alias "HederaCert$I" \
        -cacerts -storepass $CACERTS_STORE_PASS
done
