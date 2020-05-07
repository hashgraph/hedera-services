#!/usr/bin/env bash
if [ $# -lt 1 ]; then
  echo "USAGE: $0 fqcn [args]"
  exit 1
fi

. ${REPO}/.circleci/scripts/rationalize-tf-env-vars.sh

FCQN=$(echo $1 | tr -d ' ')
ARGS=$2
CLIENT_RESOURCES_REBUILD_FINGERPRINT="${REPO}/.circleci/clientResourcesRebuildDone"
cd $TEST_CLIENTS_DIR
set +x
if [ ! -f "$CLIENT_RESOURCES_REBUILD_FINGERPRINT" ]; then
  ACTIVE_WORKSPACE=$(ls -1 $TF_DIR/nets | grep test)
  CLIENT_APP_PROPS="$TF_DIR/nets/$ACTIVE_WORKSPACE/client.application.properties"
  ci_echo "Rebuilding with $CLIENT_APP_PROPS now..."
  cp $CLIENT_APP_PROPS $TEST_CLIENTS_DIR/src/main/resource/application.properties
  mvn -q clean package
  touch $CLIENT_RESOURCES_REBUILD_FINGERPRINT
fi
set -x
ci_echo "Running scenario $FCQN getting host information from properties only..."
mvn -e -q exec:java \
    -Dexec.mainClass=$FCQN \
    -Dexec.args="$ARGS" \
    -Dexec.cleanupDaemonThreads=false
