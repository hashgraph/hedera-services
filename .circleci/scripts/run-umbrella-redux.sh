#!/usr/bin/env bash

. ${REPO}/.circleci/scripts/utils.sh

cat /dev/null > ${REPO}/test-clients/output/hapi-client.log

CI_PROPERTIES_MAP=$CI_PROPERTIES_MAP \
DSL_SUITE_RUNNER_ARGS=$DSL_SUITE_RUNNER_ARGS \
${REPO}/.circleci/scripts/run-scenario-test.sh $* | tee /tmp/client.log

if [[ ${PIPESTATUS[0]} = '0' ]]; then
    echo "SUCCESS" > status.txt
else
    echo "FAIL" > status.txt
fi

${REPO}/.circleci/scripts/save-testclient-logs.sh \
   regression-testclient-logs  ${CIRCLE_BRANCH} ${CIRCLE_STAGE} status.txt

cd /tmp/
${REPO}/.circleci/scripts/save-testclient-logs.sh \
   regression-testclient-logs  ${CIRCLE_BRANCH} ${CIRCLE_STAGE} client.log

cd ${REPO}/test-clients/output
${REPO}/.circleci/scripts/save-testclient-logs.sh \
   regression-testclient-logs  ${CIRCLE_BRANCH} ${CIRCLE_STAGE} hapi-client.log

exit 0
