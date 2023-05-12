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

desc="Remove node test"
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
          "saveStatePeriod, 30"
          "throttle6, 0")

# StatsSeqDemo config          
delay=0
bytesPerTrans=100
transPerEvent=128
transPerSec=5000
#-----------------------#
#       first run       #
#-----------------------#

# calculate the freeze time
setFreezeTimeAndFreezeSettings "2 minutes"

# make settings and config
testSettings=( "${settings[@]}" "${freezeSettings[@]}" )
makeSettingsFile "${testSettings[@]}"
makeConfigFile "StatsSeqDemo.jar, 1, 3000, $delay, $bytesPerTrans, $transPerEvent, $transPerSec" $nodeName

#create local directory to hold the results
dir="$resultsDir/1_initial_run"
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

# calculate the freeze time
setFreezeTimeAndFreezeSettings "2 minutes"

# make settings with new freeze
testSettings=( "${settings[@]}" "${freezeSettings[@]}" )
makeSettingsFile "${testSettings[@]}"

#create local directory to hold the results
dir="$resultsDir/2_without_one_node"
mkdir -p "$dir"
cp "$pathToRemoteExperiment/config.txt" "$dir"

# upload the new settings file to other nodes
if [[ "$testRunningOn" != "local" ]]; then
  echo "Uploading settings to all nodes"
  # copy the new settings file to all nodes
  for i in ${!publicAddresses[@]}; do
    rsync -a -r -z -e "ssh -o StrictHostKeyChecking=no -i $pemfile" "settings.txt" "$sshUsername@${publicAddresses[$i]}:remoteExperiment/settings.txt" &
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

# get the indexes of all but the last node
indexes=( $(seq 0 $(( ${#publicAddresses[@]} - 2))) )

echo "starting all but last node"
startCertainNodes indexes[@]

sleepUntilFreeze

# stop all nodes and wait for them to finish
echo "stopping all nodes"
stopAllNodesAndWait

# download all the experiment results
getAllResults "$dir"

#-----------------------#
#       third run       #
#-----------------------#

# calculate the freeze time
setFreezeTimeAndFreezeSettings "2 minutes"

# make settings with new freeze
testSettings=( "${settings[@]}" "${freezeSettings[@]}" )
makeSettingsFile "${testSettings[@]}"

#create local directory to hold the results
dir="$resultsDir/3_with_all_nodes_again"
mkdir -p "$dir"
cp "$pathToRemoteExperiment/config.txt" "$dir"

if [[ "$testRunningOn" != "local" ]]; then
  # clean up before copying
  rm *.csv
  rm output.log
  rm swirlds.log
  rm settingsUsed.txt
  rm threadDump* 2>/dev/null
  
  # copy everything (including the saved state) to the node that was not running previously
  echo "Copying files to node previously not running"
  uploadRemoteExperimentToNode ${publicAddresses[(( ${#publicAddresses[@]} - 1))]}
  
  # change the dir name of the saved state on the new node
  ssh -o StrictHostKeyChecking=no -i $pemfile \
  "$sshUsername@${publicAddresses[(( ${#publicAddresses[@]} - 1))]}" \
  "cd remoteExperiment; mv data/saved/StatsSeqDemoMain/0 data/saved/StatsSeqDemoMain/$(( ${#publicAddresses[@]} - 1))"
  
  # copy the new settings file to all nodes
  echo "Uploading settings to all nodes"
  for i in ${!publicAddresses[@]}; do
    rsync -a -r -z -e "ssh -o StrictHostKeyChecking=no -i $pemfile" "settings.txt" "$sshUsername@${publicAddresses[$i]}:remoteExperiment/settings.txt" &
  done
  wait
else
  cd ..
  # copy the saved state to the node previously not running
  echo "Copying saved state to node previously not running"
  rm -rf data/saved/StatsSeqDemoMain/$(( ${#publicAddresses[@]} - 1))
  cp -r data/saved/StatsSeqDemoMain/0 data/saved/StatsSeqDemoMain/$(( ${#publicAddresses[@]} - 1))
  # clean up
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

echo "Test has finished"

