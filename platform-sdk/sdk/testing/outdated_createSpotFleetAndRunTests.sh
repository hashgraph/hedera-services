#!/usr/bin/env bash
#
# Copyright 2016-2022 Hedera Hashgraph, LLC
#
# This software is the confidential and proprietary information of
# Hedera Hashgraph, LLC. ("Confidential Information"). You shall not
# disclose such Confidential Information and shall use it only in
# accordance with the terms of the license agreement you entered into
# with Hedera Hashgraph.
#
# HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
# THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
# TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
# PARTICULAR PURPOSE, OR NON-INFRINGEMENT. HEDERA HASHGRAPH SHALL NOT BE LIABLE FOR
# ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
# DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
#

cd "`dirname "$0"`"

. _config.sh; . _functions.sh;

# This script performs automated tests of the Swirlds StatsDemo app on Amazon AWS.
# To use it, make sure there is exactly one .pem file and one .pub in the same directory.
#
# in the same directory as:
#
#     _config.sh                               - all the configuration needed for experiments
#     _functions.sh                            - all the functions
#     mykeyfile.pem                            - RSA private key to login to remote AWS instance
#     mykeyfile.pub                            - RSA public key to upload to AWS

if [ -z "$pemfile" ]; then
  echo "ERROR: Must have a .pem file to access AWS instances"
  exit -1
fi

echo "Getting IAM fleet role"
getAwsIamFleetRole

# request spot instances from AWS
testDir="test $desc"
mkdir -p "$testDir"
fleetIdsFile="$testDir/fleetIds.txt"
privateAddressesFile="$testDir/privateAddresses.txt"
publicAddressesFile="$testDir/publicAddresses.txt"
> "$fleetIdsFile"
for i in ${!regionList[@]}; do
  echo "Setting up region ${regionList[$i]}"
  checkAndImportPublicKey ${regionList[$i]}
  secGroupId=`getOrCreateAwsSecurityGroup ${regionList[$i]}`
  checkExitCodeAndExitOnError "getOrCreateAwsSecurityGroup"
  amiId=`getSwirldsAmiId ${regionList[$i]}`
  if [ $? -ne 0 ]; then
    echo "AMI not found in this region, please copy it and try again"
    exit -1
  fi
  echo "Starting AWS fleet in ${regionList[$i]}"
  fleetId=`startAwsSpotInstances ${regionList[$i]} ${numberOfInstancesPerRegion[$i]} ${spotPricePerRegion[$i]} $amiId $secGroupId`
  checkExitCodeAndExitOnError "startAwsInstances"
  fleetRequestIds+=($fleetId)
  printf "$fleetId\n" >> "$fleetIdsFile"
done

echo "Waiting for AWS instances to start..."
for i in ${!regionList[@]}; do
  echo "${regionList[$i]}, fleet id: ${fleetRequestIds[$i]}"
	waitForAwsFleetToStart ${regionList[$i]} ${fleetRequestIds[$i]} ${numberOfInstancesPerRegion[$i]}
done

echo "Getting AWS ip addresses"
> "$privateAddressesFile"
> "$publicAddressesFile"
for i in ${!regionList[@]}; do
  getAwsIpAddresses ${regionList[$i]} ${fleetRequestIds[$i]}
done
privateAddresses=( `cat "$privateAddressesFile"` )
publicAddresses=( `cat "$publicAddressesFile"` )

# sometimes the SSH connection is refused initially, even though the instances appear to be ready
testSshUntilWorking ${publicAddresses[0]}

echo "Uploading to node 0, ip ${publicAddresses[0]}"
uploadFromSdkToNode ${publicAddresses[0]} "$testDir/" #TODO add retry if it fails

echo "Starting all experiments"
runFunctionOnRemoteNode ${publicAddresses[0]} 'nohup bash runAllExperiments.sh >runAllExperiments.log 2>&1 &'

# sleep until experiments end
numExp=`getNumberOfExperiments`
sleep=$(( (experimentDuration+2)*numExp ))
printf "Sleeping while experiments run, will wake up at: "
#date -s "$sleep seconds"
date --date="$sleep seconds"
sleep $sleep
printf "Woke up at: "
date

runFunctionOnRemoteNode ${publicAddresses[0]} 'waitForScript "runAllExperiments"'

echo "downloading results from ${publicAddresses[0]}"
rsync -a -r -v -z -e "ssh -o StrictHostKeyChecking=no -i $pemfile" "$sshUsername@${publicAddresses[0]}:results/" results

checkExitCodeAndExitOnError "resutsDownload"

echo "Stopping AWS instances"
for i in ${!regionList[@]}; do
  echo "${regionList[$i]}, fleet id: ${fleetRequestIds[$i]}"
	stopAwsInstances ${regionList[$i]} ${fleetRequestIds[$i]}
done

rm -rf "$testDir"

