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
awsInstanceType="m4.large" # we probably don't need a better instance for these tests

desc="Restart tests 1 mem, 1 region"

# the script to run on the remote node
testScriptToRun="runRestartTests.sh"
# the time in seconds that takes the above script to finish
scriptRunningTime=480
# the jar that will be used for this test
appJarToUse="StatsSigningDemo.jar"
# AWS AMI to use
 awsAmi="ATF-U18.04-ORCJ10-PSQL10.7-BADGERIZE-V4"

sshUsername="ubuntu"
 
