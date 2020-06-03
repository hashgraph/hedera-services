#!/usr/bin/env bash

. ${REPO}/.circleci/scripts/utils.sh

TARGET_DIR=${1:-regression-testclient-logs}

echo "current circleci build URL: ${CIRCLE_BUILD_URL}" | tee -a ${REPO}/test-clients/output/hapi-client.log

cd ${REPO}/test-clients/output
/usr/local/bin/aws s3 cp hapi-client.log s3://${SWIRLD_S3_BUCKET}/${TARGET_DIR}/${CIRCLE_BRANCH}/${CIRCLE_STAGE}/hapi-client.log

# Clean up. Otherwise the content may remain in the next stage
cat /dev/null > hapi-client.log
