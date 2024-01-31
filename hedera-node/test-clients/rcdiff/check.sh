#! /bin/sh

if [ $# -lt 2 ]; then
  EXPECTED_LOC='/opt/hgcapp/recordStreams/record0.0.30'
  ACTUAL_LOC='/Users/neeharikasompalli/Documents/Hedera/Repos/repo2/hedera-services/platform-sdk/swirlds-cli/hedera-node/data/recordStreams/record0.0.30'
else
  EXPECTED_LOC=$1
  ACTUAL_LOC=$2
fi

# Change -m value to limit the number of diffs written to file
java -jar rcdiff.jar -e $EXPECTED_LOC -a $ACTUAL_LOC -m 1000
