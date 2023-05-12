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

move_files_before_deletion () {
  for f in *.$1; do 
    mv -- "$f" "${f%.$1}.$2.$1"; 
  done
}

desc="Restart tests"
resultsDir="../results/results $(date '+%Y-%m-%d %H-%M-%S') - $desc"
mkdir -p "$resultsDir"

cp "privateAddresses.txt" "$resultsDir"
cp "publicAddresses.txt" "$resultsDir"
cp "_experimentConfig.sh" "$resultsDir"

. _experimentConfig.sh

##################################################
#                    Test 1                      #
##################################################
expName="Freeze all nodes and restart"

delay=0
bytesPerTrans=100
transPerEvent=128
transPerSec=5000


# make settings and config
testSettings=( "${settings[@]}" "${freezeSettings[@]}" "saveStatePeriod, 30" )
makeSettingsFile "${testSettings[@]}"
makeConfigFile "PlatformTestingDemo.jar,  FCMRestart.json" $nodeName

#create local directory to hold the results
dir="$resultsDir/$expName"
echo "TEST: $expName"
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

# calculate the freeze time
setFreezeTimeAndFreezeSettings "3 minutes"
sleepUntilFreeze

# stop all nodes and wait for them to finish
echo "stopping all nodes"
stopAllNodesAndWait
move_files_before_deletion csv beforeFreeze.test1
mv -- "output.log" "output.beforeFreeze.test1.log";
mv -- "swirlds.log" "swirlds.beforeFreeze.test1.log";
# start all the instances
echo "starting all nodes"
startAllNodes

setFreezeTimeAndFreezeSettings "3 minutes"
sleepUntilFreeze

# stop all nodes and wait for them to finish
echo "stopping all nodes"
stopAllNodesAndWait

move_files_before_deletion csv afterFreeze.test1
mv -- "output.log" "output.afterFreeze.test1.log";
mv -- "swirlds.log" "swirlds.afterFreeze.test1.log";
# download all the experiment results
getAllResults "$dir"

# clean up
rm *.csv
rm output.log
rm swirlds.log
rm settingsUsed.txt
rm threadDump* 2>/dev/null
rm -rf data/saved/


##################################################
#                    Test 2                      #
##################################################
expName="Freeze all nodes without restart"

delay=0
bytesPerTrans=100
transPerEvent=128
transPerSec=5000


# make settings and config
testSettings=( "${settings[@]}" "${freezeSettings[@]}" "saveStatePeriod, 30" )
makeSettingsFile "${testSettings[@]}"
makeConfigFile "PlatformTestingDemo.jar,  FCMRestart.json" $nodeName

#create local directory to hold the results
dir="$resultsDir/$expName"
echo "TEST: $expName"
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

# calculate the freeze time
setFreezeTimeAndFreezeSettings "3 minutes"

# calculate the time and sleep
nowSec=`date +%s`
echo $freezeTime
freezeTimeSec=`date -u --date="$freezeTime" +%s`
sleepFor=$(( $freezeTimeSec - $nowSec + 120)); # wake up 1 minute after the freeze end
echo "Sleeping until `date -u --date="$sleepFor seconds"`"
sleep "$sleepFor"
printf "Woke up at: "
date -u

# stop all nodes and wait for them to finish
echo "stopping all nodes"
stopAllNodesAndWait

# download all the experiment results
getAllResults "$dir"

# clean up
rm *.csv
rm output.log
rm swirlds.log
rm settingsUsed.txt
rm threadDump* 2>/dev/null
rm -rf data/saved/


##################################################
#                    Test 3                      #
##################################################
expName="Restart one node without freezing"

delay=500 # slower so the node doesn't fall too much behind
bytesPerTrans=100
transPerEvent=128
transPerSec=5000

# calculate the freeze time
setFreezeTimeAndFreezeSettings "3 minutes"

# make settings and config
testSettings=( "${settings[@]}" "${freezeSettings[@]}" "saveStatePeriod, 10" )
makeSettingsFile "${testSettings[@]}"
makeConfigFile "PlatformTestingDemo.jar,  FCMRestart.json" $nodeName

#create local directory to hold the results
dir="$resultsDir/$expName"
echo "TEST: $expName"
mkdir -p "$dir"
cp "$pathToRemoteExperiment/config.txt" "$dir"

# upload all the files to other nodes
if [[ "$testRunningOn" != "local" ]]; then
  echo "Uploading files to all nodes"
  uploadRemoteExperimentToAllNodes
fi

move_files_before_deletion csv beforeFreeze.test3
mv -- "output.log" "output.beforeFreeze.test3.log";
mv -- "swirlds.log" "swirlds.beforeFreeze.test3.log";
# start all the instances
echo "starting all nodes"
startAllNodes

# sleep for a minute
sleepFor=60;
echo "Sleeping until `date -u --date="$sleepFor seconds"`"
sleep "$sleepFor"
printf "Woke up at: "
date -u

# stop node 0
echo "stopping node 0 and waiting for it to die"
ssh -o StrictHostKeyChecking=no -i $pemfile "$sshUsername@${publicAddresses[0]}" "kill \$(pgrep java)"
waitForProcess "java"

# start node 0 up again
echo "starting node 0 again"
ssh -o StrictHostKeyChecking=no -i $pemfile "$sshUsername@${publicAddresses[0]}" "cd remoteExperiment; nohup java -jar swirlds.jar -local 0 >output.log 2>&1 &" &


# let them run for another 1 minute
sleepFor=60;
echo "Sleeping until `date -u --date="$sleepFor seconds"`"
sleep "$sleepFor"
printf "Woke up at: "
date -u

# stop all nodes and wait for them to finish
echo "stopping all nodes"
stopAllNodesAndWait

# download all the experiment results
getAllResults "$dir"

# clean up
rm *.csv
rm output.log
rm swirlds.log
rm settingsUsed.txt
rm threadDump* 2>/dev/null
rm -rf data/saved/


echo "Tests have finished"


