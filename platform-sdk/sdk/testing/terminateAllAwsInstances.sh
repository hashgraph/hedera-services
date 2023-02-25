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

 findTests=`find ./ -maxdepth 1 -name "test*" 2>/dev/null`
  if [[ -z "$findTests" ]]; then
    echo "No tests found"
    exit -1
  fi

  oldifs="$IFS"
  IFS=$'\n'
  local tests
  for test in $findTests; do
    testDir=(${test:2})
    . "$testDir/_experimentConfig.sh";

    fleetIdsFile="$testDir/fleetIds.txt"
    privateAddressesFile="$testDir/privateAddresses.txt"
    publicAddressesFile="$testDir/publicAddresses.txt"
    instanceIdsFile="$testDir/instanceIds.txt"

    privateAddresses=( `cat "$privateAddressesFile"` )
    publicAddresses=( `cat "$publicAddressesFile"` )
    fleetRequestIds=( `cat "$fleetIdsFile" 2>/dev/null` )
    instanceIds=( `cat "$instanceIdsFile"` )

    echo "Terminating AWS instances"
    for i in ${!regionList[@]}; do
      regionInstances=( `getInstancesForRegion ${regionList[$i]}` )
      terminateAwsInstances ${regionList[$i]} "${regionInstances[*]}"
    done
    deleteAvailableVolumes
    rm -rf "$testDir"
  done
  IFS="$oldifs"
