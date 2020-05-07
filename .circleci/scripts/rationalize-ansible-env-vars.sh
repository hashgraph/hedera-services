#!/usr/bin/env bash

. ${REPO}/.circleci/scripts/rationalize-tf-env-vars.sh

ANSIBLE_DIR="${INFRASTRUCTURE_REPO}/terraform/deployments/ansible"

if [[ -z "${ENABLE_NEW_RELIC}" ]]; then
  ENABLE_NEW_RELIC=false
  NEW_RELIC_NAME=""
fi
