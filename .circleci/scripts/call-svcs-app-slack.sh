#!/usr/bin/env bash
set +e

. ${REPO}/.circleci/scripts/utils.sh

ensure_slackclient

PARAMS="$@"
if [[ "${CIRCLE_BRANCH}" == "master" ]]; then
  PARAMS+=("-c" "hedera-regression")
fi
echo "Params for svcs-app-slack.py: ${PARAMS[*]}"

SLACK_API_TOKEN=xoxb-344480056389-890228995125-MfvKfFwJtL0ba2Ms8HDf8656 \
  python3 ${REPO}/.circleci/scripts/svcs-app-slack.py ${PARAMS[*]}
