#!/usr/bin/env bash

. ${REPO}/.circleci/scripts/utils.sh
. ${REPO}/.circleci/scripts/rationalize-tf-env-vars.sh

DIR="$TEST_CLIENTS_DIR/freezeTimeData"
SLOW_START_HR="$(cat $DIR/hrOfExpectedStart.txt)"
SLOW_START_MIN="$(cat $DIR/minOfExpectedStart.txt)"
SLOW_START_PREFIX="$SLOW_START_HR:$SLOW_START_MIN:0"
FAST_START_PREFIX="$(cat $DIR/minBeforeExpectedStart.txt):5"

echo "TEST_CLIENTS_DIR: $TEST_CLIENTS_DIR"
echo "SLOW_START_HR: $SLOW_START_HR"
echo "SLOW_START_PREFIX: $SLOW_START_PREFIX"
echo "FAST_START_PREFIX: $FAST_START_PREFIX"

download_node_output

for HOST in ${TF_HOSTS[@]}; do
  ci_echo "Checking freeze start for $HOST now..." | tee -a ${REPO}/test-clients/output/hapi-client.log
  LOG="${REPO}/HapiApp2.0/$HOST/output/hgcaa.log"
  LINE=$(grep -e 'current platform status = MAINTENANCE' $LOG | tail -1)
  ci_echo "$LINE" | tee -a ${REPO}/test-clients/output/hapi-client.log
  FREEZE_START_PREFIX=${LINE:11:7}
  OUTCOME='failed'
  if [ $FREEZE_START_PREFIX = $SLOW_START_PREFIX ]; then
    OUTCOME='passed'
  fi
  if [ $FREEZE_START_PREFIX = $FAST_START_PREFIX ]; then
    OUTCOME='passed'
  fi
  if [ $OUTCOME = "failed" ]; then
    ci_echo "'$HOST last froze @ ${LINE}---unacceptable!'" \
       | tee -a ${REPO}/test-clients/output/hapi-client.log
    exit 1
  fi
done

ci_echo "All hosts froze successfully." | tee -a ${REPO}/test-clients/output/hapi-client.log
