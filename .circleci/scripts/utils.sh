#!/usr/bin/env bash

. ${REPO}/.circleci/scripts/ensure-builtin-env-vars.sh

function ensure_slackclient() {
  PIP_FINGERPRINT="${REPO}/.circleci/pipInstallSlackClientDone"
  if [ ! -f "$PIP_FINGERPRINT" ]; then
    ci_echo "Installing Python slackclient..."
    pip3 install wheel -q
    pip3 install slackclient -q
    touch $PIP_FINGERPRINT
    ci_echo "...done installing Python slackclient!"
  fi
}

function ci_echo() {
  echo ">>>> [CI] >> $1"
}

TEST_CLIENTS_DIR=${REPO}/test-clients
CLIENT_LOG_DIR=${REPO}/client-logs
RECORD_STREAMS_DIR=${REPO}/recordstreams
FIRST_NODE_ACCOUNT_NUM=3
HAPI_APP_DIR="/opt/hgcapp/services-hedera/HapiApp2.0"
LOG_OUTPUT_DIR="$HAPI_APP_DIR/output"
STATS_PARENT_DIR=${REPO}/ci-stats
STATS_DIR="$STATS_PARENT_DIR/current"
STATS_CSV_PREFIX=PlatformStats
INSIGHT_PY_PDF_PATH="$STATS_PARENT_DIR/insight-${CIRCLE_BUILD_NUM}.pdf"
MULTIJOB_HOST_LIST_PATH=${REPO}/.circleci/multi-job_host_list
CLIENT_RESOURCES_REBUILD_FINGERPRINT="${REPO}/.circleci/clientResourcesRebuildDone"
CLIENT_RESOURCES="$TEST_CLIENTS_DIR/src/main/resource"
LOG4J_BKUP_FINGERPRINT="${REPO}/.circleci/log4jBackedUp"
HEDERA_REGRESSION_CHANNEL='CKWHL8R9A'
JOB_SCOPED_TESTNET_FINGERPRINT="${REPO}/.circleci/jobScopedTestnet"
CACERTS_STORE_PASS="changeit"

function log_path() {
  echo "$LOG_OUTPUT_DIR/$1"
}

function run_on_remote() {
  local HOST=$1
  local CMD=$2
  echo $(ssh -o StrictHostKeyChecking=no ubuntu@$HOST "$CMD")
}

function upload_certificates_to_nodes() {
  for HOST in ${TF_HOSTS[@]}; do
    scp -p -o StrictHostKeyChecking=no \
      ${REPO}/certificates/$HOST/hedera.* ubuntu@$HOST:.ssh
    run_on_remote $HOST "ls .ssh"
  done
}

function download_node_output() {
  ACCOUNT_NO=3
  for HOST in ${TF_HOSTS[@]}; do
    TARGET_DIR="${REPO}/HapiApp2.0/$HOST"
    mkdir -p $TARGET_DIR
    touch "$TARGET_DIR/account0.0.$ACCOUNT_NO"
    ACCOUNT_NO=$((ACCOUNT_NO+1))
    scp -p -o StrictHostKeyChecking=no \
      ubuntu@$HOST:$HAPI_APP_DIR/output/*.log $TARGET_DIR
    scp -p -o StrictHostKeyChecking=no \
      ubuntu@$HOST:$HAPI_APP_DIR/*.csv $TARGET_DIR
    scp -q -p -r -o StrictHostKeyChecking=no \
      ubuntu@$HOST:$LOG_OUTPUT_DIR $TARGET_DIR
    ls -l $TARGET_DIR
  done
}

function summarize_postgresql_status() {
  LS_LOGS="ls -ltr /var/lib/postgresql/10/main/log"
  for HOST in ${TF_HOSTS[@]}; do
    ssh -o StrictHostKeyChecking=no ubuntu@$HOST \
      "sudo ${LS_LOGS} ; psql -V ; systemctl status postgresql@10-main"
  done
}

function get_binary_object_count() {
  for HOST in ${TF_HOSTS[@]}; do
    ssh -o StrictHostKeyChecking=no ubuntu@$HOST \
      "sudo -u postgres psql -d fcfs -c 'SELECT COUNT(*) FROM binary_objects' | egrep -o '[^(]+[0-9]+' | egrep -o '[^ ]*[0-9]+'" > binaryObject_$HOST.count

    num=$(cat binaryObject_$HOST.count)
    ci_echo "Number of binary objects on $HOST: $num"
  done
}

function verify_binary_object_count() {
  local diff=$1
  for HOST in ${TF_HOSTS[@]}; do
    actual=$(ssh -o StrictHostKeyChecking=no ubuntu@$HOST \
         "sudo -u postgres psql -d fcfs -c 'SELECT COUNT(*) FROM binary_objects' | egrep -o '[^(]+[0-9]+' | egrep -o '[^ ]*[0-9]+'")

    file="binaryObject_$HOST.count"

    num=$(cat "$file")

    if [[ 'num + diff' -eq actual ]]
    then
      ci_echo "SUCCESS: Binary Objects counts are $actual"
    else
      ci_echo "FAILURE: Binary Object count mismatch on $HOST, Previous: $num, Current: $actual, Expected Difference: $diff"
      exit 1
    fi
  done
}

function report_failure {
  SIG=$?
  if [[ $SIG -ne 0 ]]; then
    echo "CircleCi ${CIRCLE_BRANCH} build ${CIRCLE_BUILD_NUM} failed at stage ${CIRCLE_STAGE}" > ${REPO}/failure_msg.txt
    ${REPO}/.circleci/scripts/call-svcs-app-slack.sh \
        -c hedera-cicd \
        -t ${REPO}/failure_msg.txt
  fi
  exit $SIG
}
