#!/usr/bin/env bash
set +e

. ${REPO}/.circleci/scripts/utils.sh

ensure_slackclient

PARAMS="$@"
if [[ "${CIRCLE_BRANCH}" == "master" ]]; then
  PARAMS+=("-c" "hedera-regression")
fi
echo "Params for svcs-app-slack.py: ${PARAMS[*]}"

python3 ${REPO}/.circleci/scripts/svcs-app-slack.py ${PARAMS[*]}
