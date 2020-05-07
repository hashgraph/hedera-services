#!/usr/bin/env bash

if [ $# -lt 3 ]; then
  echo "USAGE: $0 pattern-sh timeout_secs sleep_secs"
  exit 1
fi

. ${REPO}/.circleci/scripts/utils.sh
. ${REPO}/.circleci/scripts/rationalize-tf-env-vars.sh

DEFAULT_HOST=${TF_HOSTS[0]}
TIMEOUT_SECS=$2
SLEEP_SECS=$3
LINE_PATTERN=$(eval $1)
PRE="ssh -o StrictHostKeyChecking=no ubuntu"
LOG_PTH=$(log_path swirlds.log)
GREP_CMD="${PRE}@$DEFAULT_HOST 'grep -o \"$LINE_PATTERN\" $LOG_PTH'"

SECS_WAITED=0
while true; do
  LINES=$(eval $GREP_CMD)
  ci_echo "|$LINES|"
  if [ ! -z "$LINES" ]; then
    LINES="${LINES//[/\\[}"
    LINES="${LINES//]/\\]}"
    echo $LINES > ${REPO}/.circleci/firstLateSeq.txt
    exit 0
  fi 
  SECS_WAITED=$((SECS_WAITED+SLEEP_SECS))
  if [ $SECS_WAITED -lt $TIMEOUT_SECS ]; then
    sleep $SLEEP_SECS
  else
    exit 1
  fi
done
