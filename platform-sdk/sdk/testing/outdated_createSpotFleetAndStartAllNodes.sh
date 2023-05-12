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

. _config.sh; . _functions.sh

echo "Setting up AWS"
getAwsIamFleetRole
checkAndCreateKeyPair
getOrCreateAwsSecurityGroup

echo "Starting AWS fleets"
# request spot instances from AWS
for i in ${!availabilityZones[@]}; do
    fleetRequestIds+=(`startAwsInstances ${availabilityZones[$i]} ${numberOfInstancesPerZone[$i]} ${spotPricePerZone[$i]}`)
done

echo "Waiting for AWS instances to start..."
for i in ${!availabilityZones[@]}; do
  echo "fleet id: ${fleetRequestIds[$i]}"
	waitForAwsFleetToStart ${fleetRequestIds[$i]} ${numberOfInstancesPerZone[$i]}
done

echo "Getting AWS ip addresses"
getAwsIpAddresses

makeConfigFile 0 100 1

testSshUntilWorking ${publicAddresses[0]}

echo "Uploading to node 0, ip ${publicAddresses[0]}"
uploadFromSdkToNode ${publicAddresses[0]} 

echo "Copying from node 0 to others"
runFunctionOnRemoteNode ${publicAddresses[0]} "uploadRemoteExperimentToAllNodes"

echo "Starting all nodes"
runFunctionOnRemoteNode ${publicAddresses[0]} 'startAllNodes privateAddresses[@]'
