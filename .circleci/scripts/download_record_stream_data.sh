#!/usr/bin/env bash

. ${REPO}/.circleci/scripts/rationalize-tf-env-vars.sh

set +x
mkdir -p $RECORD_STREAMS_DIR
ACCOUNT_NUM=3
for HOST in ${TF_HOSTS[@]}; do
  RECORDS_DIR="$RECORD_STREAMS_DIR/record0.0.$ACCOUNT_NUM"
  mkdir -p $RECORDS_DIR
  scp -p -o StrictHostKeyChecking=no \
    ubuntu@$HOST:$HAPI_APP_DIR/data/recordstreams/record0.0.$ACCOUNT_NUM/*.rcd* \
    $RECORDS_DIR
  ls -l $RECORDS_DIR
  ACCOUNT_NUM=$((ACCOUNT_NUM+1))
done
