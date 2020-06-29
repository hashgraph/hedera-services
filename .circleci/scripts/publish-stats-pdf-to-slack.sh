#!/usr/bin/env bash

. ${REPO}/.circleci/scripts/utils.sh

function post_summary_to_slack() {
  local CHANNEL=$1
  local FN=${CLIENT_LOG_DIR}/${WORKFLOW_NAME}-report.txt
  local LINE=$(grep "Overall Status:" $FN)
  OVERALL_STATUS="E"
  case $LINE in
    *"Passed with error"*)
      OVERALL_STATUS="W"
      ;;
    *"Failed"*)
      OVERALL_STATUS="E"
      ;;
    *"Passed"*)
      OVERALL_STATUS="P"
      ;;
  esac
  echo $OVERALL_STATUS
  ${REPO}/.circleci/scripts/call-svcs-app-slack.sh \
      -c $CHANNEL \
      -t $FN \
      -s $OVERALL_STATUS
}

SOURCE_DESC=$1
WORKFLOW_NAME=${2:-nightly-regression}
cd $STATS_PARENT_DIR
${REPO}/.circleci/scripts/prepare-slack-message.sh "$SOURCE_DESC"

# To default channel (hedera-regression)
DEFAULT_CHANNEL="hedera-cicd"

PARAMS=("-c" "$DEFAULT_CHANNEL" "-a" "-t" "$SLACK_MSG_FILE")
if [[ ! $SOURCE_DESC == *"regression"* ]]; then
    echo "Passed" >> $SLACK_MSG_FILE
    PARAMS+=("-s" "P")
fi

${REPO}/.circleci/scripts/call-svcs-app-slack.sh ${PARAMS[*]}
${REPO}/.circleci/scripts/call-svcs-app-slack.sh \
    -c $DEFAULT_CHANNEL \
    -f $INSIGHT_PY_PDF_PATH \
    -n SelectedPlatformStats

if [[ $SOURCE_DESC == *"regression"* ]]; then
  post_summary_to_slack $DEFAULT_CHANNEL
fi
