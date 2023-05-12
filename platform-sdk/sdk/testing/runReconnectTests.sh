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

if [[ "$testRunningOn" == "local" ]]
then
  # if running on local, we need a dir to keep a copy of the sdk
  tmpSdkDir="tmpSdk"
  mkdir -p "$tmpSdkDir"
  resultsDir="localResults/results $(date '+%Y-%m-%d %H-%M-%S') - $desc"
else
  resultsDir="../results/results $(date '+%Y-%m-%d %H-%M-%S') - $desc"
fi

mkdir -p "$resultsDir"

cp "privateAddresses.txt" "$resultsDir"
cp "publicAddresses.txt" "$resultsDir"
cp "_experimentConfig.sh" "$resultsDir"

#
# Cleans up any data from any previous runs
#
function cleanUp(){
  if [[ "$testRunningOn" == "local" ]]; then
    # clean up
    rm $tmpSdkDir/*.csv
    rm $tmpSdkDir/output.log
    rm $tmpSdkDir/swirlds.log
    rm $tmpSdkDir/settingsUsed.txt
    rm $tmpSdkDir/threadDump* 2>/dev/null
    rm -rf $tmpSdkDir/data/saved/

    # clean up
    rm ../*.csv
    rm ../output.log
    rm ../swirlds.log
    rm ../settingsUsed.txt
    rm ../threadDump* 2>/dev/null
    rm -rf ../data/saved/
    rm -rf ../data/fs/
  else
    # clean up
    rm *.csv
    rm output.log
    rm swirlds.log
    rm settingsUsed.txt
    rm threadDump* 2>/dev/null
    rm -rf data/saved/
    rm -rf data/fs/
  fi
}

#
# Runs a reconnect test with the supplied settings
#
# Arguments:
#   test description
#   test dir name
#   settings for settings.txt, passed as settings[@]
#   what should be done with the node that needs to reconnect, "killProcess" or "killNetwork"
#
function runReconnectTest(){
  local testDescription="$1"
  local testDirName="$2"
  local -a settings=("${!3}")
  local killType="$4"

  local reconnectNodeId=$(($totalInstances - 1))

  echo "START --- $testDescription ---"

  if [[ "$testRunningOn" == "local" && "$killType" == "killNetwork" ]]; then
    echo "Test cannot be done locally"
    return
  fi

  # make settings and config
  makeSettingsFile "${settings[@]}"
  makeConfigFile "$appConfigLine"

  #create local directory to hold the results
  local dir="$resultsDir/$testDirName"
  mkdir -p "$dir"
  cp "$pathToRemoteExperiment/config.txt" "$dir"

  # upload all the files to other nodes
  if [[ "$testRunningOn" != "local" ]]; then
    echo "Uploading files to all nodes"
    uploadRemoteExperimentToAllNodes
  else
    # if running on local, we need to make another copy of the sdk
    echo "Copying files to $tmpSdkDir"
    copySdkLocally "$tmpSdkDir"
  fi

  # start all the instances
  echo "starting all nodes"
  if [[ "$testRunningOn" != "local" ]]; then
    startAllNodes
  else
    cd ..
    java -jar swirlds.jar -local `seq -s' ' 0 $(($totalInstances - 2))` >output.log 2>&1 &
    local pid1=$!
    cd testing
    cd $tmpSdkDir
    java -jar swirlds.jar -local $(($totalInstances - 1)) >output.log 2>&1 &
    local pid2=$!
    cd ..
  fi

  echo "Sleeping for $timeBeforeNodeDown seconds"
  sleep $timeBeforeNodeDown

  if [[ "$killType" == "killProcess" ]]; then
    echo "Killing node $(($totalInstances - 1))"
    if [[ "$testRunningOn" != "local" ]]; then
      ssh -o StrictHostKeyChecking=no -i $pemfile "$sshUsername@${publicAddresses[$(($totalInstances - 1))]}" "kill \$(pgrep java)"
      ssh -o StrictHostKeyChecking=no -i $pemfile "$sshUsername@${publicAddresses[$(($totalInstances - 1))]}" "cd remoteExperiment; . _config.sh; . _functions.sh; waitForProcess \"java\""
    else
      kill $pid2
      waitForPid $pid2
    fi
  else
    # $killType is killNetwork
    local killNetworkCommand="sudo -n iptables -A INPUT -p tcp --dport 10000:65535 -j DROP; sudo -n iptables -A OUTPUT -p tcp --sport 10000:65535 -j DROP;"
    runFunctionOnRemoteNode "${publicAddresses[$reconnectNodeId]}" "$killNetworkCommand"
  fi

  echo "Sleeping for $timeNodeDown seconds"
  sleep $timeNodeDown

  if [[ "$killType" == "killProcess" ]]; then
    echo "Starting node $(($totalInstances - 1))"
    if [[ "$testRunningOn" != "local" ]]; then
      ssh -o StrictHostKeyChecking=no -i $pemfile "$sshUsername@${publicAddresses[$(($totalInstances - 1))]}" "cd remoteExperiment; nohup java -jar swirlds.jar -local $(($totalInstances - 1)) >output.log 2>&1 &"
    else
      cd $tmpSdkDir
      java -jar swirlds.jar -local $(($totalInstances - 1)) >output.log 2>&1 &
      pid2=$!
      cd ..
    fi
  else
    # $killType is killNetwork
    local resumeNetworkCommand="sudo -n iptables -D INPUT -p tcp --dport 10000:65535 -j DROP; sudo -n iptables -D OUTPUT -p tcp --sport 10000:65535 -j DROP;"
    runFunctionOnRemoteNode "${publicAddresses[$reconnectNodeId]}" "$resumeNetworkCommand"
  fi

  echo "Sleeping for $timeAfterNodeDown seconds"
  sleep $timeAfterNodeDown



  # stop all nodes and wait for them to finish
  echo "Stopping all nodes"
  if [[ "$testRunningOn" != "local" ]]; then
    stopAllNodesAndWait
  else
    kill $pid1
    kill $pid2
    waitForPid $pid1
    waitForPid $pid2
  fi


  # download all the experiment results
  getAllResults "$dir"

  if [[ "$testRunningOn" == "local" ]]; then
    # copy thread dumps from second run
    threadDumpsDir=`printf "$dir/node%04d_threadDumps/" $reconnectNodeId`
    mkdir -p "$threadDumpsDir"
    cp $tmpSdkDir/threadDump* "$threadDumpsDir"

    # copy logs from tmpSdk
    cp $tmpSdkDir/*.csv        "$dir"
    cp $tmpSdkDir/output.log   "$dir/output$(($totalInstances - 1)).log"
    cp $tmpSdkDir/swirlds.log  "$dir/swirlds$(($totalInstances - 1)).log"
  fi

  # clean up
  cleanUp

  echo "END --- $testDescription ---"
} # END function runReconnectTest()

# clean up before anz test
cleanUp

#------------------------------------------------------#
#      TEST 1: restart a node, no state on disk        #
#------------------------------------------------------#
testDescription="Test 1: restart a node, no state on disk"
testDirName="1_restart_no_state_on_disk"

testSettings=( "${settings[@]}" )
runReconnectTest "$testDescription" "$testDirName" testSettings[@] "killProcess"


#------------------------------------------------------#
#     TEST 2: restart a node, with state on disk       #
#------------------------------------------------------#
testDescription="Test 2: restart a node, with state on disk"
testDirName="2_restart_with_state_on_disk"

additionalSettings=( "saveStatePeriod,  $saveStatePeriod")
testSettings=( "${settings[@]}" "${additionalSettings[@]}" )

runReconnectTest "$testDescription" "$testDirName" testSettings[@] "killProcess"

#------------------------------------------------------#
#   TEST 3: disable network for 1 node, then enable    #
#------------------------------------------------------#
testDescription="Test 3: disable network for 1 node, then enable"
testDirName="3_disable_enable_network"

runReconnectTest "$testDescription" "$testDirName" settings[@] "killNetwork"