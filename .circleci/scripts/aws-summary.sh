#!/usr/bin/env bash

cd "`dirname "$0"`"

java \
-Dspring.output.ansi.enabled=ALWAYS \
-Daws.accessKeyId=${AWS_ACCESS_KEY_ID} \
-Daws.secretKey=${AWS_SECRET_ACCESS_KEY} \
-cp regression/regression.jar com.swirlds.regression.AWSServerCheck ${SLACK_API_TOKEN} hedera-regression
