#!/usr/bin/env bash

. ${REPO}/.circleci/scripts/ansible-functions.sh
. ${REPO}/.circleci/scripts/terraform-functions.sh

upload_certificates_to_nodes

TIMEOUT_SECS=${1:-60}
USE_HUGEPAGE=${2:-false}
USE_HUGEPAGE=$(echo ${USE_HUGEPAGE})

if [[ "$USE_HUGEPAGE" = true ]]; then
    ansible_hugepage | tee  ${REPO}/test-clients/output/hapi-client.log
else
    ansible_deploy | tee  ${REPO}/test-clients/output/hapi-client.log
fi
wait_for_live_hosts 50211 $TIMEOUT_SECS | tee -a ${REPO}/test-clients/output/hapi-client.log
