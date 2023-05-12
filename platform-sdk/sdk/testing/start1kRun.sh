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
#     _test*.sh                                - the configuration for the particular test that needs to be run
#     _config.sh                               - global config
#     _functions.sh                            - all the functions
#     mykeyfile.pem                            - RSA private key to login to remote AWS instance
#     mykeyfile.pub                            - RSA public key to upload to AWS

if [ -z "$pemfile" ]; then
  echo "ERROR: Must have a .pem file to access AWS instances"
  exit -1
fi

# choose a test to be run
if [[ -z $1 ]]
  then
    chooseTestToRun
    testConfig=$optionChosen
  else
    testConfig="$1"
fi

# load the configuration
. "$testConfig";

testDir="test $(date '+%Y-%m-%d %H-%M-%S') -- $desc"
mkdir -p "$testDir"
privateAddressesFile="$testDir/privateAddresses.txt"
publicAddressesFile="$testDir/publicAddresses.txt"
instanceIdsFile="$testDir/instanceIds.txt"
> "$instanceIdsFile"
createExperimentConfig "$testConfig" "$testDir"

#------------------------------------------------------------------------#
#                        start instances on AWS                          #
#------------------------------------------------------------------------#
for i in ${!regionList[@]}; do
  echo "Setting up region ${regionList[$i]}"
  checkAndImportPublicKey ${regionList[$i]}
  secGroupId=`getOrCreateAwsSecurityGroup ${regionList[$i]}`
  checkExitCodeAndExitOnError "getOrCreateAwsSecurityGroup"
  amiId=`getSwirldsAmiId ${regionList[$i]} "$awsAmi"`
  if [ $? -ne 0 ]; then
    echo "AMI not found in this region, please copy it and try again"
    exit -1
  fi
  echo "Starting instances in ${regionList[$i]}"
  `startAwsInstances ${regionList[$i]} ${numberOfInstancesPerRegion[$i]} $amiId $awsInstanceType $secGroupId >> "$instanceIdsFile"`
  checkExitCodeAndExitOnError "startAwsInstancesFromTemplate"
done
instanceIds=( `cat "$instanceIdsFile"` )

#------------------------------------------------------------------------#
#                     wait for instances to start                        #
#------------------------------------------------------------------------#
for i in ${!regionList[@]}; do
  regionInstances=( `getInstancesForRegion ${regionList[$i]}` )
  #echo "${regionInstances[*]}"
  waitForAwsInstancesToStart ${regionList[$i]} ${#regionInstances[@]} "${regionInstances[*]}"
done

#------------------------------------------------------------------------#
#                          get IP addresses                              #
#------------------------------------------------------------------------#
> "$privateAddressesFile"
> "$publicAddressesFile"
for i in ${!regionList[@]}; do
  regionInstances=( `getInstancesForRegion ${regionList[$i]}` )
  getAwsIpAddresses ${regionList[$i]} "${regionInstances[*]}"
done
privateAddresses=( `cat "$privateAddressesFile"` )
publicAddresses=( `cat "$publicAddressesFile"` )

#------------------------------------------------------------------------#
#                      upload files to node 0                            #
#------------------------------------------------------------------------#
# sometimes the SSH connection is refused initially, even though the instances appear to be ready
testSshUntilWorking ${publicAddresses[0]}
echo "Uploading to node 0, ip ${publicAddresses[0]}"
filesToUpload=("$testScriptToRun" "data/apps/$appJarToUse")
while true; do
  uploadFromSdkToNode ${publicAddresses[0]} "$testDir/" filesToUpload[@]
  if [ $? -eq 0 ]; then
    break
  else
    echo "Upload failed, trying again"
  fi
done

echo "Starting all experiments"
runFunctionOnRemoteNode ${publicAddresses[0]} "nohup bash $testScriptToRun >$testScriptToRun.log 2>&1 &"

# sleep until experiments end
sleep=$scriptRunningTime
printf "Sleeping while experiments run, will wake up at: "
#date -s "$sleep seconds"
date --date="$sleep seconds"
wakeupAt=`date --date="$sleep seconds" +%s`
while [[ true ]]; do
  sleep 30
  if [ `date +%s` -ge $wakeupAt ]; then
    break
  fi
  #date
done
printf "Woke up at: "
date

runFunctionOnRemoteNode ${publicAddresses[0]} "waitForScript $testScriptToRun"

echo "Downloading results from ${publicAddresses[0]}"
while true; do
  rsync -a -r -v -z -e "ssh -o StrictHostKeyChecking=no -i $pemfile" "$sshUsername@${publicAddresses[0]}:results/" results/resulsts-1Ksize
  if [ $? -eq 0 ]; then
    break
  else
    echo "Download failed, trying again"
  fi
done

# terminate all the instances
for i in ${!regionList[@]}; do
  regionInstances=( `getInstancesForRegion ${regionList[$i]}` )
  terminateAwsInstances ${regionList[$i]} "${regionInstances[*]}"
done

rm -rf "$testDir"


