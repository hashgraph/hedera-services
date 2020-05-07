#!/bin/bash -eo pipefail

export REPO=~/tmp/services-hedera
export INFRASTRUCTURE_REPO=~/tmp/infrastructure
export TF_WORKSPACE=net-hightps
export CIRCLE_BUILD_NUM=fixed
export TF_DIR=~/tmp/infrastructure/terraform/deployments/aws-4-node-psql-swirlds
export USE_EXISTING_NETWORK="1"

cd ../../


CI_PROPERTIES_MAP="" \
DSL_SUITE_RUNNER_ARGS="TopicGetInfoSpecs" \
.circleci/scripts/trap-failable-for-tf-cleanup.sh \
    '/repo/.circleci/scripts/run-scenario-test.sh \
        com.hedera.services.bdd.suites.SuiteRunner \
        0 \
        3'
