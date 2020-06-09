#!/usr/bin/env bash
if [ $# -lt 4 ]; then
  echo "USAGE: $0 cfg host-index node-account is-unavail-grpc [unavail-resp-timeout-secs]"
  exit 1
fi

. ${REPO}/.circleci/scripts/rationalize-tf-env-vars.sh

CONFIG_FILE=$(echo $1 | tr -d ' ')
TF_HOST_INDEX=$2
NODE_ACCOUNT=$(echo $3 | tr -d ' ')
GRPC_SHOULD_BE_UNAVAILABLE=$(echo $4 | tr -d ' ')
CONFIG_DIR="$TEST_CLIENTS_DIR/config"
ACTIVE_PROPERTIES="$CONFIG_DIR/umbrellaTest.properties"

cp "$CONFIG_DIR/$CONFIG_FILE" $ACTIVE_PROPERTIES
if [ $GRPC_SHOULD_BE_UNAVAILABLE = "true" ]; then
  LOG_FILE="$TEST_CLIENTS_DIR/umbrellaRun.log"
  TIMEOUT_SECS=${5:-120}
  set +e
  cd $TEST_CLIENTS_DIR
  NODE=${TF_HOSTS[$2]}
  mvn -q exec:java \
      -Dexec.mainClass=com.hedera.services.legacy.regression.umbrella.UmbrellaTest \
      -Dexec.args="$NODE $NODE_ACCOUNT" \
      -Dexec.cleanupDaemonThreads=false >$LOG_FILE 2>&1 &
  MVN_PID=$!
  ci_echo "Running umbrella test to detect unavailability, PID=$MVN_PID"

  SECS_WAITED=0
  SLEEP_SECS=$((TIMEOUT_SECS/10))
  DETECTED_UNAVAILABILITY=false
  while true; do
    grep -e 'UNAVAILABLE' $LOG_FILE
    if [ $? -eq 0 ]; then
      DETECTED_UNAVAILABILITY=true
      break
    fi
    ci_echo "No sign of frozen hosts yet after ${SECS_WAITED} secs..."
    cat $LOG_FILE | tail -10
    SECS_WAITED=$((SECS_WAITED+SLEEP_SECS))
    if [ $SECS_WAITED -lt $TIMEOUT_SECS ]; then
      sleep $SLEEP_SECS
    else
      break
    fi
  done

  kill $MVN_PID
  set -e
  if [ $DETECTED_UNAVAILABILITY = "false" ]; then
    ci_echo "Remote nodes expected frozen, but appear available after $TIMEOUT_SECS secs!"
    exit 1
  fi
else
  ${REPO}/.circleci/scripts/run-scenario-test.sh \
      com.hedera.services.legacy.regression.umbrella.UmbrellaTest \
      $TF_HOST_INDEX \
      $NODE_ACCOUNT  | tee ${REPO}/test-clients/output/hapi-client.log
fi
