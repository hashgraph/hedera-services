#!/usr/bin/env bash

if [ $# -lt 1 ]; then
  echo "USAGE: $0 catastrophe-pattern-sh"
  exit 1
fi

. ${REPO}/.circleci/scripts/rationalize-tf-env-vars.sh

PRE="ssh -o StrictHostKeyChecking=no ubuntu"
LOG_PTH=$(log_path hgcaa.log)
LINE_PATTERN=$(eval $1)
for HOST in ${TF_HOSTS[@]}; do
  GREP_CMD="${PRE}@$HOST 'grep \"$LINE_PATTERN\" $LOG_PTH'"
  LINE_COUNT=$(eval $GREP_CMD | wc -l)
  if [ $LINE_COUNT -gt 0 ]; then
    ci_echo "**** FATAL ERROR ****"
    ci_echo "Catastrophic failure in $HOST logs, exiting."
    exit 1
  fi
done
