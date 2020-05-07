#!/bin/bash

. ${TOOLS_PATH}/run/functions.sh

prompt_with_scratch_default host "Host of fee schedule to update"
HOST=$VALUE
prompt_with_scratch_default port 'Port on host'
PORT=$VALUE
prompt_with_scratch_default payer 'Payer account to use'
PAYER=$VALUE
prompt_with_scratch_default b64keypair 'Base64-encoded keypair for payer'
B64KEYPAIR=$VALUE
prompt_with_scratch_default feeScheduleJson 'path to JSON'
FEE_SCHEDULE_JSON=$VALUE

TARGET_NODE="$HOST" TARGET_PORT="$PORT" \
  java -jar ${TOOLS_PATH}/run/SysFilesUpdate.jar UPDATE \
    "$PAYER" $B64KEYPAIR "111" $FEE_SCHEDULE_JSON
