#! /bin/sh

if [ $# -lt 2 ]; then
  EXPECTED_LOC='/Users/michaeltinker/Dev/hedera-services/platform-sdk/swirlds-cli/mono-record-streams'
  ACTUAL_LOC='/Users/michaeltinker/Dev/hedera-services/platform-sdk/swirlds-cli/hedera-node/data/recordStreams/record0.0.30'
else
  EXPECTED_LOC=$1
  ACTUAL_LOC=$2
fi

java -jar rcdiff.jar -e $EXPECTED_LOC -a $ACTUAL_LOC
