#!/usr/bin/env bash

cd "`dirname "$0"`"

java \
-Dspring.output.ansi.enabled=ALWAYS \
-Daws.accessKeyId=${AWS_ACCESS_KEY_ID} \
-Daws.secretKey=${AWS_SECRET_ACCESS_KEY} \
-cp regression/regression.jar com.swirlds.regression.AWSServerCheck \
xoxp-344480056389-344925970834-610132896599-fb69be9200db37ce0b0d55a852b2a5dc \
UEVPV4HDY
