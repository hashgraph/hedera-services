#!/usr/bin/env bash

. ${REPO}/.circleci/scripts/utils.sh

mkdir -p $DIAG_DIR

WORKFLOW_DESC="<n/a>"
if [[ -n "$1" ]]; then
  WORKFLOW_DESC="$1"
fi

LINK_TXT="CircleCI Job #${CIRCLE_BUILD_NUM}"
echo "<${CIRCLE_BUILD_URL}|${LINK_TXT}>" > $SLACK_MSG_FILE
echo "Branch: ${CIRCLE_BRANCH}" >> $SLACK_MSG_FILE
echo "Commit ID: ${CIRCLE_SHA1}" >> $SLACK_MSG_FILE
echo "Workflow: ${WORKFLOW_DESC}" >> $SLACK_MSG_FILE
