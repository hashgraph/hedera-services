#!/usr/bin/env bash

. ${REPO}/.circleci/scripts/utils.sh

mkdir -p $DIAG_DIR

LINK_TXT="CircleCI Job #${CIRCLE_BUILD_NUM}"
echo "<${CIRCLE_BUILD_URL}|${LINK_TXT}>" > $SLACK_MSG_FILE
echo "Branch: ${CIRCLE_BRANCH}" >> $SLACK_MSG_FILE
echo "Commit ID: ${CIRCLE_SHA1}" >> $SLACK_MSG_FILE

echo "WORKFLOW_NAME: ${WORKFLOW_NAME}"
if [[ -n "$1" ]]; then
  workflow_name=$1
else
  workflow_name=${WORKFLOW_NAME}
fi

echo "Workflow: ${workflow_name}" >> $SLACK_MSG_FILE
echo "Stage: ${CIRCLE_STAGE}" >> $SLACK_MSG_FILE
