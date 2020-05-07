#!/usr/bin/env bash

. ${REPO}/.circleci/scripts/utils.sh

ci_echo "Checking fingerprint '$JOB_SCOPED_TESTNET_FINGERPRINT'..."
if [ -f $JOB_SCOPED_TESTNET_FINGERPRINT ]; then
  VAR_FILE=${1:-'ci.tfvars'}
  ci_echo "cleanup-testnet"
  ${REPO}/.circleci/scripts/destroy-tf-workspace.sh $VAR_FILE
else
  ci_echo "...no job-scoped testnet present."
fi
