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

####################################
#
# SSH config
#
####################################

sshUsername="ec2-user"
pubfile=$(find *.pub -print -quit 2>/dev/null)
pemfile=$(find /home/*.pem -print -quit 2>/dev/null) 
if [ $? -ne 0 ]
then
  # this is needed because of Windows
  pemfile=$(find *.pem -print -quit 2>/dev/null)
fi

####################################
#
# Automatic loading of variables
#
####################################

privateAddresses=( `cat privateAddresses.txt 2>/dev/null` )
publicAddresses=( `cat publicAddresses.txt 2>/dev/null` )
localIps=`/sbin/ifconfig | awk '/inet addr/{print substr($2,6)}'` #/sbin is not in the path when running a remote command
if [ -n "$pemfile" ]; then
  chmod 600 $pemfile
fi
if [[ `pwd` == *"remoteExperiment"* ]]; then
  pathToRemoteExperiment="../remoteExperiment"
else
  if [[ "$testRunningOn" == "local" ]]; then
    pathToRemoteExperiment=".."
  else
    pathToRemoteExperiment="remoteExperiment"
  fi
fi

####################################
#
# AWS related
#
####################################

numberOfInstancesPerAwsRequest=100


####################################
#
# Additional JVM Options
#
####################################
EXTRA_JVM_OPTS="-Xmx30g -Xms20g"


####################################
#
# Auto-detect Date Command
#
####################################
export DATE="/usr/bin/env date"

if [[ "$OSTYPE" == "darwin"* ]]; then
    export DATE="/usr/bin/env gdate"
fi