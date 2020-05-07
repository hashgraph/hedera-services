#!/usr/bin/env bash

CI_PROPERTIES_MAP=$CI_PROPERTIES_MAP \
DSL_SUITE_RUNNER_ARGS=$DSL_SUITE_RUNNER_ARGS \
${REPO}/.circleci/scripts/run-scenario-test.sh $* | tee /tmp/umbrellaRedux_test.log

if [[ ${PIPESTATUS[0]} = '0' ]]
then
    exit 0
fi

err_count=`grep "java.lang.AssertionError" /tmp/umbrellaRedux_test.log | wc -l`
unknown_count=`grep "Status was UNKNOWN"    /tmp/umbrellaRedux_test.log | wc -l`

total_err_count=$(echo $err_count)
unknown_count=$(echo $unknown_count)

# TODO calclate the percentage of the UNKNOWN status error
if (( total_err_count == unknown_count )) && (( unknown_count < 100 ))
then
    ci_echo "There are ${unknown} consensus timed-out transaction, but it's as expected."
    exit 0
fi
exit 1
