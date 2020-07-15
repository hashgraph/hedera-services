#!/usr/bin/env bash

. ${REPO}/.circleci/scripts/utils.sh

#if [ $# -lt 1 ]; then
#  ci_echo "USAGE: $0 workflow-name [branch-dir]"
#  exit 1
#fi

WORKFLOW=${1:none-workflow}
TARGET_DIR="${WORKFLOW}-logs"
BRANCH_DIR=${2:-${CIRCLE_BRANCH}}

/usr/local/bin/aws s3 rm --recursive s3://${SWIRLD_S3_BUCKET}/${TARGET_DIR}/${BRANCH_DIR}
