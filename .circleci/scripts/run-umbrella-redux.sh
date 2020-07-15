#!/usr/bin/env bash

. ${REPO}/.circleci/scripts/utils.sh

cat /dev/null > ${REPO}/test-clients/output/hapi-client.log

CI_PROPERTIES_MAP=$CI_PROPERTIES_MAP \
DSL_SUITE_RUNNER_ARGS=$DSL_SUITE_RUNNER_ARGS \
${REPO}/.circleci/scripts/run-scenario-test.sh $*

${REPO}/.circleci/scripts/save-default-client-logs.sh nightly-regression

exit 0
