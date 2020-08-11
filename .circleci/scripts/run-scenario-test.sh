#!/usr/bin/env bash

if [ $# -lt 3 ]; then
  echo "USAGE: $0 fqcn node-terraform-index node-account [other-args]"
  exit 1
fi

. ${REPO}/.circleci/scripts/rationalize-tf-env-vars.sh

FCQN=$(echo $1 | tr -d ' ')
NODE=${TF_HOSTS[$2]}
NODE=$(echo $NODE | tr -d ' ')
if [ -z $NODE ]; then
  ci_echo "Suggested host is unknown...exiting!"
  exit 1
fi
NODE_ACCOUNT=$(echo $3 | tr -d ' ')

I=0
ALL_NODES=""
while [ $I -lt ${#TF_HOSTS[@]} ]; do
  NEXT_IP=${TF_HOSTS[$I]}
  NEXT_ACCOUNT="0.0.$((I+3))"
  NEXT_NODE="${NEXT_IP}:${NEXT_ACCOUNT}"
  if [ $I -eq 0 ]; then
    ALL_NODES=$NEXT_NODE
  else
    ALL_NODES="${ALL_NODES},${NEXT_NODE}"
  fi
  I=$((I+1))
done

OTHER_ARGS=$4
cd $TEST_CLIENTS_DIR
if [ ! -f "$CLIENT_RESOURCES_REBUILD_FINGERPRINT" ]; then
  if [[ -z $USE_EXISTING_NETWORK ]]; then
    ACTIVE_WORKSPACE=$(ls -1 $TF_DIR/nets | grep test)
  else
    ACTIVE_WORKSPACE=$TF_WORKSPACE
  fi
  CLIENT_APP_PROPS="$TF_DIR/nets/$ACTIVE_WORKSPACE/client.application.properties"
  ci_echo "Rebuilding with $CLIENT_APP_PROPS now..."
  cp $CLIENT_APP_PROPS $TEST_CLIENTS_DIR/src/main/resource/application.properties
  mvn -q clean package
  touch $CLIENT_RESOURCES_REBUILD_FINGERPRINT
fi
ci_echo "Running legacy scenario $FCQN vs $ALL_NODES (suggested ${NODE}:0.0.${NODE_ACCOUNT})..."

NODES=$ALL_NODES \
  mvn -e -q exec:java \
    -Dexec.mainClass=$FCQN \
    -Dexec.args="$NODE $NODE_ACCOUNT $OTHER_ARGS" \
    -Dexec.cleanupDaemonThreads=false
