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

#
# run one experiment 
# (this uses globals, including the loop variables below from runAllExperiments)
#
runExperiment () 
{
	expCount=$((expCount+1))
  
  settings=( "maxOutgoingSyncs,  $callers"
             "useTLS,            $useTLS" 
             "verifyEventSigs,   $verifyEventSigs" 
             "throttle7extra,    $throttle7extra" 
             "multiSocketNumber, $multiSocketNumber"
             "multiSocketTransferBytesPerSocket, $multiSocketBytes"
             "csvFileName, PlatformTesting" )
  settings=( "${settings[@]}" "${additionalSettings[@]}" )
  
  makeSettingsFile "${settings[@]}"

  # if not running multiple nodes on single AWS instance
  # then use default makeConfigFile, otherwise, use makeMultiNodeConfigFile
  if [[ -z $MULTI_NODES_ON_SINGLE_AWS ]] ; then 
    if [[ "$appJarToUse" == "PlatformTestingDemo.jar" ]]
    then
      jsonConfigFile=( `cat json_file_name.txt` )
      makeConfigFile "$appJarToUse,  $jsonConfigFile" $nodeName
    
    else
      makeConfigFile "$appJarToUse, 1, 3000, $delay, $bytesPerTrans, $transPerEvent, $transPerSec" $nodeName
    fi
  else
    if [[ "$appJarToUse" == "PlatformTestingDemo.jar" ]]
    then
      jsonConfigFile=( `cat json_file_name.txt` )
      makeMultiNodeConfigFile "$appJarToUse,  $jsonConfigFile"
    
    else
      makeMultiNodeConfigFile "$appJarToUse, 1, 3000, $delay, $bytesPerTrans, $transPerEvent, $transPerSec"
    fi
  fi
  
  ####### create local directory to hold the results #######
	expName="$callers-call_$bytesPerTrans-bpt_$transPerEvent-tpe_$transPerSec-tps_$useTLS-TLS_$multiSocketNumber-sock_$multiSocketBytes-msb_$experimentDuration-duration"
	dir="$resultsDir/$expName"
	echo $expName
	mkdir -p "$dir"
  cp config.txt "$dir"
  cp *.txt "$dir"

  echo "$expName" > "$resultsDir/expName.txt"

  uploadRemoteExperimentToAllNodes
  
  ####### start all the instances running #######
  killAfter=15
  if [[ -n $STREAM_SERVER ]] ; then
    echo "starting all stream servers"
    for i in ${!privateAddresses[@]}; do
      ssh -o StrictHostKeyChecking=no -i $pemfile "$sshUsername@${publicAddresses[$i]}" "cd remoteExperiment; . _config.sh; . _functions.sh; timeout -k $killAfter $((experimentDuration + 30))s nohup java -Dkey=data/keys/private-stream.pfx -Dcert=data/keys/public.pfx -Dport=50051 -classpath swirlds.jar:data/apps/PlatformTestingDemo.jar com.swirlds.demo.platform.streamevent.StreamServerDemo -local $i >output.log 2>&1 &" &
    done  
  elif [[ -n $RESTART_TEST ]] ; then  
    echo "restart test : starting all nodes"
    for i in ${!privateAddresses[@]}; do
      ssh -o StrictHostKeyChecking=no -i $pemfile "$sshUsername@${publicAddresses[$i]}" \
      "cd remoteExperiment; . _config.sh; . _functions.sh; rm -rf data/fs ; rm -rf data/saved; rm -rf data/platformtesting;  timeout -k $killAfter $((experimentDuration /3)) nohup java $MIRROR_JVM_OPTS $EXTRA_JVM_OPTS -jar swirlds.jar -local $i >output.log 2>&1 ;  echo -e '\n\n' >> output.log ; echo -e '\n\n' >> swirlds.log;  cp swirlds.log swirlds2.log; timeout -k $killAfter $((experimentDuration /3)) nohup java $MIRROR_JVM_OPTS $EXTRA_JVM_OPTS -jar swirlds.jar -local $i >>output.log 2>&1 ;  echo -e '\n\n' >> output.log ; echo -e '\n\n' >> swirlds.log; cp swirlds.log swirlds3.log;  timeout -k $killAfter $((experimentDuration /3)) nohup java $MIRROR_JVM_OPTS $EXTRA_JVM_OPTS -jar swirlds.jar -local $i >>output.log 2>&1 &" &
    done  
  else
    echo "starting all nodes"
    for i in ${!privateAddresses[@]}; do
      if [[ -n $MULTI_NODES_ON_SINGLE_AWS ]] ; then
        # run without local argument to be able to launch all nodes on same AWS instances
        ssh -o StrictHostKeyChecking=no -i $pemfile "$sshUsername@${publicAddresses[$i]}" "cd remoteExperiment; . _config.sh; . _functions.sh; timeout -k $killAfter $((experimentDuration + 30))s nohup java $MIRROR_JVM_OPTS $EXTRA_JVM_OPTS -jar swirlds.jar >output.log 2>&1 &" &
      else
        ssh -o StrictHostKeyChecking=no -i $pemfile "$sshUsername@${publicAddresses[$i]}" "cd remoteExperiment; . _config.sh; . _functions.sh; timeout -k $killAfter $((experimentDuration + 30))s nohup java $MIRROR_JVM_OPTS $EXTRA_JVM_OPTS -jar swirlds.jar -local $i >output.log 2>&1 &" &
      fi
    done   
  fi

# wait is not needed here anymore, kept in case we need to revert to old code in the future.
#  wait
	####### let them run for a while #######
	numLeft=$(( numExp - expCount ))
	timeLeft=$(( ((numLeft+1) * experimentDuration)/60 ))
	echo "About to run $expCount of $numExp, ($numLeft to go), $timeLeft min until end"
	echo "Experiment: $expName"
	echo "Running the experiment for $experimentDuration seconds. Please wait ..."
	sleep "$experimentDuration"
  
  ####### download all the experiment results #######
  getAllResults "$dir"
  
  ####### wait for the java processes in all the instances #######
  runCommandOnAllNodes 'waitForProcess "java"'
  
  ####### download all the experiment results #######
  # getAllResults "$dir"
  
  rm *.csv
  rm output.log
  rm swirlds.log
  rm settingsUsed.txt
  rm threadDump* 2>/dev/null
  #rm data.tar.gz
}

#
# Runs all the experiements defined by the global config
# in _config.sh
#


# run one experiment for 2 minutes for which we won't
# keep the results because they are always bad
privateAddresses=( `cat privateAddresses.txt` )
publicAddresses=( `cat publicAddresses.txt` )
useTLS=${useTLSList[0]}
callers=${syncCallersList[0]}
delay=${delayList[0]}
bytesPerTrans=${bytesPerTransList[0]}
transPerEvent=${transPerEventList[0]}
transPerSec=${transPerSecList[0]}
throttle7extra=${throttle7extraList[0]}
verifyEventSigs=${verifyEventSigsList[0]}
multiSocketNumber=${multiSocketNumberList[0]}
multiSocketBytes=${multiSocketBytesList[0]}
numExp=1
expCount=0
oldExperimentDuration=$experimentDuration
experimentDuration=120


resultsDir="../resultstmp"
mkdir -p "$resultsDir"

#skip startup experiment for stream server test
if [[ -n $STREAM_SERVER ]] ; then
  echo "Stream server mode"
elif [[ -n $STREAM_CLIENT ]] ; then
  echo "Stream server mode"
elif [[ -n $RESTART_TEST ]] ; then
  echo "Restart test skip pilot run"
else
  echo "Running startup experiment, these results will be thrown away"
  runExperiment
  
  # remove pilot run result, otherwise may pollute the following formal run
  rm *.log; rm -rf data/fs ; rm -rf data/saved; rm -rf data/platformtesting ; 
fi


rm -rf "../resultstmp"
# this ends the startup experiment, now the ones that count start


resultsDir="../results/results $(date '+%Y-%m-%d %H-%M-%S') - $desc"
mkdir -p "$resultsDir"
echo "$resultsDir" > "resultsDir.txt"
expCount=0
numExp=`getNumberOfExperiments`

experimentDuration=$oldExperimentDuration

cp "privateAddresses.txt" "$resultsDir"
cp "publicAddresses.txt" "$resultsDir"
cp "_experimentConfig.sh" "$resultsDir"
  
for multiSocketBytes in "${multiSocketBytesList[@]}"; do
  for useTLS in "${useTLSList[@]}"; do
    for callers in "${syncCallersList[@]}"; do
      for delay in "${delayList[@]}"; do
        for bytesPerTrans in "${bytesPerTransList[@]}"; do
          for transPerEvent in "${transPerEventList[@]}"; do
            for transPerSec in "${transPerSecList[@]}"; do
              for throttle7extra in "${throttle7extraList[@]}"; do
                for verifyEventSigs in "${verifyEventSigsList[@]}"; do
                  for multiSocketNumber in "${multiSocketNumberList[@]}"; do
                    runExperiment
                  done
                done
              done
            done
          done
        done
      done
    done
  done
done




