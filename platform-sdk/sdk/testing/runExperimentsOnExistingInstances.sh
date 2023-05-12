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

# selects the instances to be used
chooseTest

# choose a test to be run
chooseTestToRun
testConfig=$optionChosen

# load the configuration
. "$testConfig";

# copy the new configuration
cp "$testConfig" "$testDir/_experimentConfig.sh"

echo "Stopping all nodes"
stopAllNodesAndWait

echo "Deleting results from node 0, ip:${publicAddresses[0]}"
runFunctionOnRemoteNode "${publicAddresses[0]}" "cd ..; rm -rf results;"


echo "Re-uploading files to node 0, ip:${publicAddresses[0]}"
filesToUpload=("$testScriptToRun" "data/apps/$appJarToUse")
while true; do
  uploadFromSdkToNode ${publicAddresses[0]} "$testDir/" filesToUpload[@]
  if [ $? -eq 0 ]; then
    break
  else
    echo "Upload failed, trying again"
  fi
done

echo "Starting the test script"
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


runFunctionOnRemoteNode ${publicAddresses[0]} "waitForScript $testScriptToRun"

function download_result() {
  echo "Downloading results from ${publicAddresses[0]}"
  while true; do
    rsync -a -r -v -z -e "ssh -o StrictHostKeyChecking=no -i $pemfile" "$sshUsername@${publicAddresses[0]}:results/" "results"
    if [ $? -eq 0 ]; then
      break
    else
      echo "Download failed, trying again"
    fi
  done
}

download_result
