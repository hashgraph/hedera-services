#!/usr/bin/env bash

. ${REPO}/.circleci/scripts/utils.sh
. ${REPO}/.circleci/scripts/rationalize-tf-env-vars.sh

set +e
cd $REPO

WORKFLOW_NAME=${1:-nightly-regression}

TARGET_DIR="${WORKFLOW_NAME}-logs"
BRANCH_DIR=${2:-${CIRCLE_BRANCH}}
CLIENT_LOG_DIR=${REPO}/client-logs

ci_echo "Download all test client log files to ${CLIENT_LOG_DIR}..."
/usr/local/bin/aws s3 cp \
  s3://${SWIRLD_S3_BUCKET}/${TARGET_DIR}/${BRANCH_DIR} ${CLIENT_LOG_DIR} --recursive

echo "Summary of this regression test:" > ${CLIENT_LOG_DIR}/${WORKFLOW_NAME}-report.txt

python3 ${REPO}/.circleci/scripts/summarize-test-results.py  -w ${WORKFLOW_NAME}
