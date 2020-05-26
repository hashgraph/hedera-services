#!/usr/bin/env bash

. ${REPO}/.circleci/scripts/utils.sh

CI_PROPERTIES_MAP=$CI_PROPERTIES_MAP \
DSL_SUITE_RUNNER_ARGS=$DSL_SUITE_RUNNER_ARGS \
${REPO}/.circleci/scripts/run-scenario-test.sh $* | tee /tmp/client.log

if [[ ${PIPESTATUS[0]} = '0' ]]
    echo "SUCCESS" > status.txt
then
    echo "FAIL" > status.txt
fi

${REPO}/.circleci/scripts/save-testclient-logs.sh \
   regression-testclient-logs  ${CIRCLE_BRANCH} ${CIRCLE_BUILD_NUM} status.txt

cd /tmp/
${REPO}/.circleci/scripts/save-testclient-logs.sh \
   regression-testclient-logs  ${CIRCLE_BRANCH} ${CIRCLE_BUILD_NUM} client.log

exit 0
