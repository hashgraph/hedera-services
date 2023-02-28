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

# All the columns in the stats file in order
allColumns=( "" ""  badEv/sec bytes/sec_catchup bytes/sec_sent bytes/sec_sync bytes/sec_sys bytes/sec_trans bytes/trans bytes/trans_sys cEvents/sec conns cpuLoadSys discEvReq dupEv% dupEv/sec ev/syncR ev/syncS events/sec eventsInMem fracSyncSlowed icSync/sec irSync/sec local memberID members memFree memMax memTot name ping proc q1 q2 q3 q4 rounds/sec roundSup sec/sync sec/sync1 sec/sync2 sec/sync3 sec/sync4 secC2C SecC2H secC2R secC2RC secNewSigState secOR2T secR2C secR2F secR2nR secSC2T secStateCopy secTransH simCallSyncsMax simSyncs sleep1/sec sleep2/sec sleep3/sec sync/secC sync/secR time timeFracAdd timeFracDot TLS trans trans/event trans/event_sys trans/sec trans/sec_sys transH/sec write Dig/sec DigBatches/sec DigBatchSz DigLockUp/sec DigPulse/sec DigQuDepth DigSliceSz DigSpans/sec DigSubWrkItmTime DigWrkTime MaxDigBatchSz MaxSigBatchSz MinDigBatchSz MinSigBatchSz PlatSigEnqueueTime PlatSigExpandTime Sig/sec SigBatches/sec SigBatchSz SigIntakeEnqueueTime SigIntakeListSize SigIntakePulse/sec SigIntakePulseTime SigIntakeQueueDepth SigInval/sec SigLockUp/sec SigPulse/sec SigQuDepth SigSliceSz SigSpans/sec SigSubWrkItmTime SigVal/sec SigWrkTime TtlDig TtlSig TtlSigInval TtlSigVal )

# The columns to check the values of
checkColumns=(  "q2"  "secC2C"  "secTransH"  "trans/sec")
# The maximum allowed value for each column
maxValues=(     300   4         200          120        )

#
# Returns the index of the supplied column name
#
# Arguments:
#   column name
#
function findIndex(){
  for i in ${!allColumns[@]}; do 
    if [[ "${allColumns[i]}" == "$1" ]]; then
      echo "$i"
      break
    fi
  done
}

#for i in ${!allColumns[@]}; do  echo "${allColumns[i]}"; done

for i in ${!checkColumns[@]}; do 
  columnIndexes[$i]=`findIndex "${checkColumns[i]}"`
done

echo "Reading input"
while IFS= read -r line || [ -n "$line" ]; do
  #printf '%s\n' "$line"
  IFS=',' read -ra values <<< "$line"
  if [[ ${#values[@]} -ne ${#allColumns[@]} ]]; then
    echo "Cannot parse line, skipping"
    continue
  fi
  #echo ${#values[@]}
  #echo ${#allColumns[@]}
  #for i in ${!values[@]}; do  echo "${values[i]}"; done
  echo "------------------------------"
  for i in ${!columnIndexes[@]}; do
    intValue=${values[columnIndexes[i]]%.*}
    if [ -z "${intValue##*[!0-9]*}" ]; then
       echo "cannot parse '${values[columnIndexes[i]]%.*}': Not a number"
       continue 2
    fi
    
    printf "%10s=%10s   " ${checkColumns[i]} ${values[columnIndexes[i]]};
    
    if [[ "$intValue" -lt "${maxValues[i]}" ]]; then
      echo "OK"
    else
      echo "ERROR value too large"
    fi
  done
done
