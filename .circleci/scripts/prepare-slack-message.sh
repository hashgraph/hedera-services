#!/usr/bin/env bash

. ${REPO}/.circleci/scripts/utils.sh

mkdir -p $DIAG_DIR

LINK_TXT="CircleCI Job #${CIRCLE_BUILD_NUM}"
echo "<${CIRCLE_BUILD_URL}|${LINK_TXT}>" > $SLACK_MSG_FILE
echo "Branch: ${CIRCLE_BRANCH}" >> $SLACK_MSG_FILE
echo "Commit ID: ${CIRCLE_SHA1}" >> $SLACK_MSG_FILE
if [[ -n "$1" ]]; then
  echo "Workflow: $1" >> $SLACK_MSG_FILE
else
  echo "Stage: ${CIRCLE_STAGE}" >> $SLACK_MSG_FILE
fi
