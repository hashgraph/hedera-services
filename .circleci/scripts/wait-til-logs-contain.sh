#!/usr/bin/env bash

if [ $# -lt 5 ]; then
  echo "USAGE: $0 log pattern-sh req-count timeout-secs sleep-secs"
  exit 1
fi


LOG_FILE=$1
LINE_PATTERN=$(eval $2)

. ${REPO}/.circleci/scripts/utils.sh
. ${REPO}/.circleci/scripts/host-checking-utils.sh
. ${REPO}/.circleci/scripts/rationalize-tf-env-vars.sh

REQ_COUNT=$3
TIMEOUT_SECS=$4
SLEEP_SECS=$5

N=${#TF_HOSTS[@]}
SECS_WAITED=0
LOG_PTH=$(log_path $LOG_FILE)
PRE="ssh -o StrictHostKeyChecking=no ubuntu"
init_checklist $N
echo ">>>> [CI] >> Logs all need $REQ_COUNT of '$LINE_PATTERN'..." \
   | tee -a ${REPO}/test-clients/output/hapi-client.log

while true; do
  I=0
  while [ $I -lt $N ]; do
    if [ ${HOSTS_CHECKED[$I]} -eq 0 ]; then
      GREP_CMD="${PRE}@${TF_HOSTS[$I]} 'grep \"$LINE_PATTERN\" $LOG_PTH'"
      COUNT=$(eval $GREP_CMD | wc -l)
      if [ $COUNT -eq $REQ_COUNT ]; then
        check_host $I
      fi
    fi
    I=$((I+1))
  done
  are_all_hosts_checked
  if [ $? -eq 0 ]; then
    ci_echo "All $LOG_FILE had $REQ_COUNT '$LINE_PATTERN' occurrences." \
        | tee -a ${REPO}/test-clients/output/hapi-client.log
    exit 0
  fi
  SECS_WAITED=$((SECS_WAITED+SLEEP_SECS))
  if [ $SECS_WAITED -lt $TIMEOUT_SECS ]; then
    ci_echo "Sleeping $SLEEP_SECS secs at `date` ..." \
       | tee -a ${REPO}/test-clients/output/hapi-client.log
    sleep $SLEEP_SECS
  else
    break
  fi
done
ci_echo "Time's up! ($TIMEOUT_SECS secs expired.)" | tee -a ${REPO}/test-clients/output/hapi-client.log
ci_echo "Not all $LOG_FILE had $REQ_COUNT '$LINE_PATTERN' occurrences!" \
   | tee -a ${REPO}/test-clients/output/hapi-client.log
ci_echo ${TF_HOSTS[@]} | tee -a ${REPO}/test-clients/output/hapi-client.log
ci_echo ${HOSTS_CHECKED[@]} | tee -a ${REPO}/test-clients/output/hapi-client.log
exit 1
