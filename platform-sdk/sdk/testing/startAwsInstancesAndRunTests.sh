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

if [[ -z $2 ]]
  then
    resultsFolder="results"
  else
    resultsFolder="results/$2"
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
#                     JSON config for Platform Tesing                    #
#------------------------------------------------------------------------#

if [[ "$appJarToUse" == "PlatformTestingDemo.jar" ]]
then
  if [[ -n $3 ]] ; then
    jsonConfigFile=$(basename $3)
    echo "Json file = " $jsonConfigFile
    echo $jsonConfigFile > "json_file_name.txt"
  fi
fi

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
#                          User interrupt handling                       #
#------------------------------------------------------------------------#
# trap control C so if user interrupt the script, auto stop and delete AWS instances
trap user_interrupt 2

function terminate_all_instances()
{
  # terminate all the instances
  for i in ${!regionList[@]}; do
    regionInstances=( `getInstancesForRegion ${regionList[$i]}` )
    terminateAwsInstances ${regionList[$i]} "${regionInstances[*]}"
  done
}

function user_interrupt() {
  # terminate all the instances
  echo
  echo
  echo "User interrupt detected "
  echo

  while true; do
      read -p "You interrupted the test, want to retrieve results before killing all AWS instances (Y/N) ?" yn
      case $yn in
          [Yy]* ) retrieve_result=true;  break;;
          [Nn]* ) retrieve_result=false; break;;
          * ) echo "Please answer yes or no.";;
      esac
  done
  if $retrieve_result 
  then
      echo "Retrieving results ... "
      stopAllNodes
      runFunctionOnRemoteNode ${publicAddresses[0]} "waitForScript $testScriptToRun"
      download_result
  else
      echo "Not retrieve results"
  fi
  terminate_all_instances
  exit
}

function download_result() {
  echo "Downloading results from ${publicAddresses[0]}"
  while true; do
    rsync -a -r -v -z -e "ssh -o StrictHostKeyChecking=no -i $pemfile" "$sshUsername@${publicAddresses[0]}:results/" $resultsFolder
    if [ $? -eq 0 ]; then
      break
    else
      echo "Download failed, trying again"
    fi
  done
}

#
# Scan for SUCCESS or ERROR keyword from log files when running PlatformTestingApp
#
function scan_keyowords_in_logs () {
  # get the latest directory created by download_result
  LASTDIR=$(ls -td $resultsFolder/* | head -1)
  
  echo "LASTDIR=$LASTDIR"

  echo "Search *.log for TEST SUCCESS key words"
  find "$LASTDIR" -name "*.log" -exec grep -l "TEST SUCCESS" '{}' + > test_success_summary.log

  echo -e "\n\n"

  echo "Search *.log for TEST ERROR key words"
  find "$LASTDIR" -name "*.log" -exec grep -l "TEST ERROR" '{}' +  > test_error_summary.log

}

#------------------------------------------------------------------------#
#                     Mirror network options                            #
#------------------------------------------------------------------------#

# if mirrornet mode
if [[ -n $MIRROR_JVM_OPTS ]] ; then
  echo " ---- mirror_server.txt --- list "
  makeMirrorAddrLocalFile
  cat mirror_server.txt

  # pause wait mainnet to launch to get main net address books
  echo
  echo "Waiting, please launch mainnet now ........"
  echo
  pause
fi

# if mainnet mode
if [[ -n $MAINNET_MODE ]] ; then
  echo " ---- mainnet_config.txt --- list "
  makeMainNetAdddrForMirrorNode
  cat mainnet_config.txt

  echo
  echo "Waiting, please contiue with mirror net, after upload over then continue with main net  ........"
  echo
  pause
fi

#------------------------------------------------------------------------#
#               Generate keys for streaming mode                         #
#------------------------------------------------------------------------#

# if generate 4th key pair for streaming event
if [[ -n $ENABLE_EVENT_STREAM ]] ; then
  generateKeysForNodes
fi

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


#------------------------------------------------------------------------#
#                   start test, wait for times up                        #
#------------------------------------------------------------------------#
echo "Starting all experiments"
runFunctionOnRemoteNode ${publicAddresses[0]} "nohup bash $testScriptToRun >$testScriptToRun.log 2>&1 &"

# sleep until experiments end
sleep=$scriptRunningTime
printf "Sleeping while experiments run, will wake up at: "

#mas os date cmd option is different
if [[ "$OSTYPE" == "darwin"* ]] ;  then
  date -j -v+"${sleep}S"
  wakeupAt=`date -j -v+"${sleep}S"  +%s`
else
  date --date="$sleep seconds"
  wakeupAt=`date --date="$sleep seconds" +%s`
fi

echo 'wakeupAt =' $wakeupAt 

login_name="$sshUsername@${publicAddresses[0]}:remoteExperiment/*.log"
ssh_cmd="ssh -o StrictHostKeyChecking=no -i $pemfile"

echo
echo
echo 'Before test end, you can peek log file using command:《 rsync -a -r -v -z -e "'  $ssh_cmd '" ' $login_name ' temp 》 '
echo
echo 'You can stop current AWS test with Ctrl-C'
echo 
if [[ -n $MIRROR_JVM_OPTS ]] ; then
  echo
  echo "You can continue with mainnet launch after a moment ..."
  echo
fi

while [[ true ]]; do
  sleep 30
  if [ `date +%s` -ge $wakeupAt ]; then
    break
  fi
  #date
done
printf "Woke up at: "
date
date +%s

#------------------------------------------------------------------------#
#                    retrieve test results                               #
#------------------------------------------------------------------------#

runFunctionOnRemoteNode ${publicAddresses[0]} "waitForScript $testScriptToRun"

download_result

terminate_all_instances

rm -rf "$testDir"

# scan test result key word for PlatformTesingDemo
if [[ "$appJarToUse" == "PlatformTestingDemo.jar" ]] 
then
  scan_keyowords_in_logs
fi


# notify the user the test is done
echo -ne '\007'

