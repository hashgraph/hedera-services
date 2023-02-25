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

#########################
# The command line help #
#########################
display_help() {
 echo "********************************************"
 echo "Usage: $0 script iterations " >&2
 echo
 echo "script: script to repeatedly run"
 echo "iteration: number of times to run the script"
 echo "********************************************"
}

if [[ $# -ne 2 ]] ; then
    display_help
    exit 1
fi
script=$1
interations=$2
echo "script: $script"
echo "iter: $interations"
baseName=$(basename $script)
echo "$baseName"
counter=0
while [ $((counter)) -lt $((interations)) ]
do
  ($script &)
  echo "ps -opid= -C $baseName"
  pids=`ps -opid= -C $baseName`
  echo "pid: $pids"
  #while [ -d /proc/$pid ] ; do
  for pid in $pids
  do
    while kill -0 $pid 2>> /dev/null; do
      sleep 30
    done
  done
  sleep 2m
  let "counter++"
  echo starting test $counter
done
echo finished
(terminateAllAwsInstances.sh)
exit 1
