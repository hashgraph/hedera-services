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

. _config.sh; . _functions.sh; . _experimentConfig.sh;

desc="Replace keys test"
if [[ "$testRunningOn" == "local" ]]
then
  resultsDir="localResults/results $(date '+%Y-%m-%d %H-%M-%S') - $desc"
else
  resultsDir="../results/results $(date '+%Y-%m-%d %H-%M-%S') - $desc"
fi

mkdir -p "$resultsDir"

cp "privateAddresses.txt" "$resultsDir"
cp "publicAddresses.txt" "$resultsDir"
cp "_experimentConfig.sh" "$resultsDir"

settings=("maxOutgoingSyncs,  1"
          "useTLS,            1" 
          "verifyEventSigs,   1" 
          "throttle7extra,    0.05" 
          "multiSocketNumber, 1"
          "multiSocketTransferBytesPerSocket, 1460" 
          "numConnections, 1000"
          "throttle7, 1"
          "throttle7threshold, 1.5"
          "useRSA, 1" 
          "useCBC, 0" 
          "lockLogThreadDump, 0"
          "lockLogTimeout, 4000"
          "lockLogBlockTimeout, 4000"
          "showInternalStats, 1"
          "saveStatePeriod, 30")

# StatsSeqDemo config          
delay=0
bytesPerTrans=100
transPerEvent=128
transPerSec=5000
#-----------------------#
#       first run       #
#-----------------------#

generateKeysForNodes

# calculate the freeze time
setFreezeTimeAndFreezeSettings "2 minutes"

# make settings and config
testSettings=( "${settings[@]}" "${freezeSettings[@]}" )
makeSettingsFile "${testSettings[@]}"
makeConfigFile "StatsSeqDemo.jar, 1, 3000, $delay, $bytesPerTrans, $transPerEvent, $transPerSec" $nodeName

#create local directory to hold the results
dir="$resultsDir/1_first_key_set"
mkdir -p "$dir"
cp "$pathToRemoteExperiment/config.txt" "$dir"

# upload all the files to other nodes
if [[ "$testRunningOn" != "local" ]]; then
  echo "Uploading files to all nodes"
  uploadRemoteExperimentToAllNodes
fi

# start all the instances
echo "starting all nodes"
startAllNodes

sleepUntilFreeze

# stop all nodes and wait for them to finish
echo "stopping all nodes"
stopAllNodesAndWait

# download all the experiment results
getAllResults "$dir"

#-----------------------#
#      second run       #
#-----------------------#

# delete the old keys and generate new ones
deleteKeysForNodes
generateKeysForNodes

# calculate the freeze time
setFreezeTimeAndFreezeSettings "2 minutes"

# make settings with new freeze
testSettings=( "${settings[@]}" "${freezeSettings[@]}" )
makeSettingsFile "${testSettings[@]}"

#create local directory to hold the results
dir="$resultsDir/2_second_key_set"
mkdir -p "$dir"
cp "$pathToRemoteExperiment/config.txt" "$dir"

# upload the new settings file to other nodes
if [[ "$testRunningOn" != "local" ]]; then
  echo "Uploading settings and keys to all nodes"
  # copy the new settings file and keys to all nodes
  for i in ${!publicAddresses[@]}; do
    rsync -a -r -z -e "ssh -o StrictHostKeyChecking=no -i $pemfile" "settings.txt" "$sshUsername@${publicAddresses[$i]}:remoteExperiment/settings.txt" &
    rsync -a -r -z -e "ssh -o StrictHostKeyChecking=no -i $pemfile" "data/keys" "$sshUsername@${publicAddresses[$i]}:remoteExperiment/data/" &
  done
  wait
else
  # clean up
  cd ..
  rm *.csv
  rm output.log
  rm swirlds.log
  rm settingsUsed.txt
  rm threadDump* 2>/dev/null
  cd testing
fi

# start all the instances
echo "starting all nodes"
startAllNodes

sleepUntilFreeze

# stop all nodes and wait for them to finish
echo "stopping all nodes"
stopAllNodesAndWait

# download all the experiment results
getAllResults "$dir"

# clean up keys
deleteKeysForNodes

#------ END -------

echo "Test has finished"

