#!/usr/bin/env bash

. ${REPO}/.circleci/scripts/utils.sh
. ${REPO}/.circleci/scripts/rationalize-tf-env-vars.sh

date -u
for HOST in ${TF_HOSTS[@]}; do
  RUN_ON_REMOTE="ssh -o StrictHostKeyChecking=no ubuntu@$HOST"
  APT_PS="$RUN_ON_REMOTE \"ps -ef | grep [a]pt\""
  while true; do 
    ACTIVE_APT_CMDS=$(eval $APT_PS)
    if [ -n "$ACTIVE_APT_CMDS" ]; then
      ci_echo "Sleeping 10s while apt processes finish ($ACTIVE_APT_CMDS)..."
      sleep 10
    else
      break
    fi
  done
  APT_MARK_HOLD="$RUN_ON_REMOTE \"sudo apt-mark hold postgresql-10\""
  eval $APT_MARK_HOLD
done
