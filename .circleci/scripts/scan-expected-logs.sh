#!/usr/bin/env bash

#
# Usage scan a string pattern in logs, if not found exit with 
# error messsage
#
# Arguments
#   $1 string pattern
#   $2 log file name
if [ $# -lt 1 ]; then
  echo "USAGE: $0 string-pattern"
  exit 1
fi

. ${REPO}/.circleci/scripts/rationalize-tf-env-vars.sh

PRE="ssh -o StrictHostKeyChecking=no ubuntu"
LOG_PTH=$(log_path $2)
LINE_PATTERN=$1
for HOST in ${TF_HOSTS[@]}; do
  GREP_CMD="${PRE}@$HOST 'grep \"$LINE_PATTERN\" $LOG_PTH'"
  LINE_COUNT=$(eval $GREP_CMD | wc -l)
  if [ $LINE_COUNT -eq 0 ]; then
    ci_echo "**** FATAL ERROR ****"
    ci_echo "Not found ($1) in $HOST log $2, exiting."
    exit 1
  fi
done
