#!/usr/bin/env bash

. ${REPO}/.circleci/scripts/utils.sh

mkdir -p ${REPO}/diagnostics
MSG_FILE=${REPO}/diagnostics/slack_msg.txt

WORKFLOW_DESC="<n/a>"
if [[ -n "$1" ]]; then
  WORKFLOW_DESC="$1"
fi

LINK_TXT="CircleCI Job #${CIRCLE_BUILD_NUM}"
echo "<${CIRCLE_BUILD_URL}|${LINK_TXT}>" > $MSG_FILE
echo "Branch: ${CIRCLE_BRANCH}" >> $MSG_FILE
echo "Commit ID: ${CIRCLE_SHA1}" >> $MSG_FILE
echo "Workflow: ${WORKFLOW_DESC}" >> $MSG_FILE
