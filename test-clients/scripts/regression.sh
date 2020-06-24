#!/usr/bin/env bash

NODES="3.19.223.98:0.0.3" \
DSL_SUITE_RUNNER_ARGS="TopicCreateSpecs SubmitMessageSpecs -TLS=off -NODE=random" \
mvn -e -q exec:java \
    -Dexec.mainClass="com.hedera.services.bdd.suites.SuiteRunner"  -Dexec.args="13.59.42.186 3" \
    -Dexec.cleanupDaemonThreads=false

