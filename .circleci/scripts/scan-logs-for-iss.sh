#!/usr/bin/env bash

if [ $# -lt 1 ]; then
  echo "USAGE: $0 iss-pattern-sh"
  exit 1
fi

. ${REPO}/.circleci/scripts/rationalize-tf-env-vars.sh

PRE="ssh -o StrictHostKeyChecking=no ubuntu"
LOG_PTH=$(log_path swirlds.log)
LINE_PATTERN=$(eval $1)
for HOST in ${TF_HOSTS[@]}; do
  GREP_CMD="${PRE}@$HOST 'grep \"$LINE_PATTERN\" $LOG_PTH'"
  LINE_COUNT=$(eval $GREP_CMD | wc -l)
  if [ $LINE_COUNT -gt 0 ]; then
    ci_echo "**** FATAL ERROR ****"
    ci_echo "Invalid state signature in $HOST logs, exiting."
    exit 1
  fi
done
