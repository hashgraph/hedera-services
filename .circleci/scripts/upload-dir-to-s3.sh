#!/usr/bin/env bash

. ${REPO}/.circleci/scripts/utils.sh

if [ $# -lt 3 ]; then
  ci_echo "USAGE: $0 upload-dir artifact-dir s3-artifact-name"
  exit 1
fi

UPLOAD_DIR=$1
TAR_DIR=$2
TGZ_ARTIFACT="${2}.tgz"
TGZ_ARTIFACT_PATH="${REPO}/$TGZ_ARTIFACT"
cd $UPLOAD_DIR
tar -zcvf $TGZ_ARTIFACT_PATH ./$TAR_DIR
/usr/local/bin/aws s3 cp \
  $TGZ_ARTIFACT_PATH \
  s3://${SWIRLD_S3_BUCKET}/${CIRCLE_BRANCH}/$TGZ_ARTIFACT
#  | tee /repo/test-clients/output/hapi-client.log
