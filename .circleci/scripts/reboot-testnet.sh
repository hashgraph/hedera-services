#!/usr/bin/env bash

. ${REPO}/.circleci/scripts/ansible-functions.sh
. ${REPO}/.circleci/scripts/terraform-functions.sh

TIMEOUT_SECS=${1:-60}

ansible_reboot
wait_for_live_hosts 50211 $TIMEOUT_SECS
