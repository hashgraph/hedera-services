#!/usr/bin/env bash

. ${REPO}/.circleci/scripts/utils.sh

if [ $# -lt 1 ]; then
  ci_echo "USAGE: $0 target-dir [branch-dir]"
  exit 1
fi

TARGET_DIR=${1:regression-testclient-logs}
BRANCH_DIR=${2:-${CIRCLE_BRANCH}}

/usr/local/bin/aws s3 rm --recursive s3://${SWIRLD_S3_BUCKET}/${TARGET_DIR}/${BRANCH_DIR}
