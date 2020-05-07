#!/usr/bin/env bash

. ${REPO}/.circleci/scripts/utils.sh

function set_tf_hosts_list {
  if [ -f $MULTIJOB_HOST_LIST_PATH ]; then
    HOSTS_LIST_PATH=$MULTIJOB_HOST_LIST_PATH
  else
    HOSTS_LIST_PATH="$TF_DIR/nets/$TF_WORKSPACE/hosts_list"
    cp $HOSTS_LIST_PATH $MULTIJOB_HOST_LIST_PATH || true
  fi

  if [ -f $HOSTS_LIST_PATH ]; then
    TF_HOSTS=($(cat $HOSTS_LIST_PATH))
    echo ">>>> [CI] >> ${TF_HOSTS[@]}"
  else 
    TF_HOSTS=()
    echo ">>>> [CI] >> No host list is available yet...just a head's up."
  fi
}

if [[ -z "${TF_WORKSPACE}" ]]; then
  TF_WORKSPACE="test-${CIRCLE_BUILD_NUM}"
fi

set_tf_hosts_list

VAR_FILE_MEMORY_PATH=${REPO}/.circleci/var-file-name.txt
