#!/usr/bin/env bash

NUM_HOSTS=${1:-4}
VAR_FILE=${3:-'ci.tfvars'}
NEW_LIVENESS_TIMEOUT_SECS=${2:-120}
OLD_LIVENESS_TIMEOUT_SECS=${4:-5}

. ${REPO}/.circleci/scripts/terraform-functions.sh

set +e
wait_for_live_hosts 50211 $OLD_LIVENESS_TIMEOUT_SECS
if [ $? -eq 0 ]; then
  exit 0
fi

ci_echo "No testnet, likely due to re-running from a failed job...recreating!"
cd $TF_DIR
rm -rf nets/$TF_WORKSPACE
rm -rf $MULTIJOB_HOST_LIST_PATH
export TF_WORKSPACE=''

. ${REPO}/.circleci/scripts/rationalize-tf-env-vars.sh
set -e

# provision-testnet-hosts
${REPO}/.circleci/scripts/trap-failable-for-tf-cleanup.sh \
  "${REPO}/.circleci/scripts/create-tf-workspace.sh \
      $NUM_HOSTS \
      $NEW_LIVENESS_TIMEOUT_SECS \
      $VAR_FILE"

# deploy-testnet-nodes
${REPO}/.circleci/scripts/trap-failable-for-tf-cleanup.sh \
    '${REPO}/.circleci/scripts/deploy-testnet.sh'

# pause
sleep 30

# disallow postgres apt upgrades
${REPO}/.circleci/scripts/trap-failable-for-tf-cleanup.sh \
  "${REPO}/.circleci/scripts/disallow-postgres-upgrade.sh"
