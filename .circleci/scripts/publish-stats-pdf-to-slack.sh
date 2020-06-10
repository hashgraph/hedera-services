#!/usr/bin/env bash

. ${REPO}/.circleci/scripts/utils.sh

function post_summary_to_slack() {
  local CHANNEL=$1
  local FN=${CLIENT_LOG_DIR}/regression-test-summary.txt
  local LINE=$(grep "Overall Status:" $FN)
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
cd $STATS_PARENT_DIR
${REPO}/.circleci/scripts/prepare-slack-message.sh "$SOURCE_DESC"

# To default channel (hedera-regression)
DEFAULT_CHANNEL="hedera-regression"

PARAMS=("-c" "$DEFAULT_CHANNEL" "-a" "-t" "$SLACK_MSG_FILE")
if [[ ! $SOURCE_DESC == *"regression"* ]]; then
    PARAMS += ("-s" "P")
fi

${REPO}/.circleci/scripts/call-svcs-app-slack.sh ${PARAMS[*]}
${REPO}/.circleci/scripts/call-svcs-app-slack.sh \
    -c $DEFAULT_CHANNEL \
    -f $INSIGHT_PY_PDF_PATH \
    -n SelectedPlatformStats

${REPO}/.circleci/scripts/call-svcs-app-slack.sh \
        -t ${REPO}/${CLIENT_DIR}/regression-test-summary.txt
