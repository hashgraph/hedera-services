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

desc="Add node test"
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
          "showInternalStats, 1")
          
expName="Freeze, stop, add node, start"

# copy the full list of addresses to another variable
allPrivateAddresses=( ${privateAddresses[@]} )
allPublicAddresses=( ${publicAddresses[@]} )

# keep adding nodes until the total number of nodes
for (( curInstNum = $startWithInstanceNumber; curInstNum <= $totalInstances; curInstNum++ )); do

  # take only the addresses we are using now
  privateAddresses=( ${allPrivateAddresses[@]:0:$curInstNum} )
  publicAddresses=( ${allPublicAddresses[@]:0:$curInstNum} )

  delay=0
  bytesPerTrans=100
  transPerEvent=128
  transPerSec=5000

  # calculate the freeze time, the platform checks UTC time, so we set it according to the UTC clock
  freezeTime=`date -u --date="2 minutes"`
  freezeTimeHour=`date -u --date="$freezeTime" +"%H"`
  freezeTimeMinute=`date -u --date="$freezeTime" +"%M"`
  # remove the seconds from freeze time
  freezeTime=`date -u --date="$freezeTimeHour:$freezeTimeMinute:00"`

  # make settings and config
  testSettings=("freezeActive, 1"
                "freezeTimeStartHour, $freezeTimeHour"
                "freezeTimeStartMin, $freezeTimeMinute" 
                "freezeTimeEndHour, $freezeTimeHour"
                "freezeTimeEndMin, $freezeTimeMinute"
                "saveStatePeriod, 30")
  testSettings=( "${settings[@]}" "${testSettings[@]}" )
  makeSettingsFile "${testSettings[@]}"
  makeConfigFile "StatsSeqDemo.jar, 1, 3000, $delay, $bytesPerTrans, $transPerEvent, $transPerSec" $nodeName

  #create local directory to hold the results
  dir="$resultsDir/run_with_${curInstNum}_instances"
  mkdir -p "$dir"
  cp "$pathToRemoteExperiment/config.txt" "$dir"
  
  if [[ $curInstNum == $startWithInstanceNumber ]]; then
    # on the first run upload all the files to other nodes
    if [[ "$testRunningOn" != "local" ]]; then
      echo "Uploading files to all nodes"
      uploadRemoteExperimentToAllNodes
    fi
  else
    # on subsequent runs, copy all files only to the new node
    if [[ "$testRunningOn" != "local" ]]; then
      # clean up before copying
      rm *.csv
      rm output.log
      rm swirlds.log
      rm settingsUsed.txt
      rm threadDump* 2>/dev/null
      
      # copy everything (including the saved state) to the node that was not running previously
      uploadRemoteExperimentToNode ${publicAddresses[(( ${#publicAddresses[@]} - 1))]}
      
      # change the dir name of the saved state on the new node
      ssh -o StrictHostKeyChecking=no -i $pemfile \
      "$sshUsername@${publicAddresses[(( ${#publicAddresses[@]} - 1))]}" \
      "cd remoteExperiment; mv data/saved/StatsSeqDemoMain/0 data/saved/StatsSeqDemoMain/$(( ${#publicAddresses[@]} - 1))"
      
      # copy the new config file to all nodes
      for i in ${!publicAddresses[@]}; do
        rsync -a -r -z -e "ssh -o StrictHostKeyChecking=no -i $pemfile" "config.txt" "$sshUsername@${publicAddresses[$i]}:remoteExperiment/config.txt" &
      done
      wait
    else
      cd ..
      # copy the saved state for the new node
      cp -r data/saved/StatsSeqDemoMain/0 data/saved/StatsSeqDemoMain/$(( ${#publicAddresses[@]} - 1))
      # clean up
      rm *.csv
      rm output.log
      rm swirlds.log
      rm settingsUsed.txt
      rm threadDump* 2>/dev/null
      cd testing
    fi
  fi

  # start all the instances
  echo "starting $curInstNum nodes"
  if [[ "$testRunningOn" == "local" ]]
  then
    cd ..
    java -jar swirlds.jar >output.log 2>&1 &
    cd testing
  else
    for i in ${!privateAddresses[@]}; do
      ssh -o StrictHostKeyChecking=no -i $pemfile "$sshUsername@${publicAddresses[$i]}" "cd remoteExperiment; nohup java -jar swirlds.jar -local $i >output.log 2>&1 &" &
    done
    wait
  fi

  # calculate the time and sleep
  nowSec=`date +%s`
  freezeTimeSec=`date -u --date="$freezeTime" +%s`
  sleepFor=$(( $freezeTimeSec - $nowSec + 15)); # wake up 15 sec after freeze start
  echo "Sleeping until `date -u --date="$sleepFor seconds"`"
  sleep "$sleepFor"
  printf "Woke up at: "
  date -u

  # stop all nodes and wait for them to finish
  echo "stopping all nodes"
  if [[ "$testRunningOn" == "local" ]]
  then
    kill $(pgrep java)
    waitForProcess "java"
    
    # copy logs from before the add node
    cp ../*.csv                "$dir"
    cp ../output.log           "$dir"
    cp ../swirlds.log          "$dir"
    cp ../config.txt           "$dir"
    cp ../settings.txt         "$dir"
    cp ../settingsUsed.txt     "$dir"
    cp ../threadDump*          "$dir" 2>/dev/null
    cp -r ../data/saved        "$dir"
  else
    stopAllNodes
    runCommandOnAllNodes 'waitForProcess "java"'
    # download all the experiment results
    getAllResults "$dir"
  fi
done

echo "Test has finished"

