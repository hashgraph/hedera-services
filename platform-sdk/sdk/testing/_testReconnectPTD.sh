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

#
####################################
#
# AWS instance config
#
# regionList, list AWS regions where to start spot instances
# numberOfInstancesPerRegion, list of numbers that corresponds to the number of instances to start in each region
# spotPricePerRegion, list of maximum prices for spot instances that correspond to each region
#
####################################
#              Virgina     Oregon        Canada      São Paulo       Sydney        Frankfurt         Seoul            Tokyo
#regionList=("us-east-1" "us-west-2" "ca-central-1" "sa-east-1" "ap-southeast-2" "eu-central-1" "ap-northeast-2" "ap-northeast-1")

regionList=("us-east-1")
numberOfInstancesPerRegion=(4)
awsInstanceType="m4.4xlarge"

desc="Reconnect PTD FCM tests"

totalInstances=0
for i in ${numberOfInstancesPerRegion[@]}; do
  let totalInstances+=$i
done

# the amount of time the network should run before one node goes down, in seconds
timeBeforeNodeDown=60
# the amount of time a node should stay down, in seconds
timeNodeDown=60
# the amount of time the network should run after the down node has startd again
timeAfterNodeDown=120
# the setting that will be used by all tests
settings=("maxOutgoingSyncs,  2"
          "showInternalStats, 1"
          "csvFileName, ReconnectStats"
          "csvAppend, 1"
          "reconnectActive, 1")
# the value for the saveStatePeriod setting, when used
saveStatePeriod="60"

# the script to run on the remote node
testScriptToRun="runReconnectTests.sh"
# the time in seconds that takes the above script to finish
scriptRunningTime=$(((timeBeforeNodeDown + timeNodeDown + timeAfterNodeDown)*3))
# the jar that will be used for this test
appJarToUse="PlatformTestingDemo.jar"
# app config
appConfigLine="PlatformTestingDemo.jar, FCM1K.json"
# AWS AMI to use
awsAmi="Hashgraph Java 10"
  