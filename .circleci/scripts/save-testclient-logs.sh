#!/usr/bin/env bash

. ${REPO}/.circleci/scripts/utils.sh

if [ $# -lt 4 ]; then
  ci_echo "USAGE: $0 target-dir branch-dir test-client-id log-file"
  exit 1
fi

TARGET_DIR=$1
BRANCH_DIR=$2
CHILD_DIR=$3
LOG_FILE=$4
/usr/local/bin/aws s3 cp \
  $LOG_FILE \
  s3://${SWIRLD_S3_BUCKET}/${TARGET_DIR}/${BRANCH_DIR}/${CHILD_DIR}/$LOG_FILE
