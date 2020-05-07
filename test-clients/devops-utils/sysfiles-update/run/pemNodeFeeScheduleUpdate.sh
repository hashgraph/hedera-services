#!/bin/bash

. ${TOOLS_PATH}/run/functions.sh

prompt_with_scratch_default host "Host of fee schedule to update"
HOST=$VALUE
prompt_with_scratch_default port 'Port on host'
PORT=$VALUE
prompt_with_scratch_default payer 'Payer account to use'
PAYER=$VALUE
prompt_with_scratch_default pem 'PEM file for payer'
PEM_FILE=$VALUE
prompt_with_scratch_default passphrase 'Passphrase for PEM file'
PASSPHRASE=$VALUE
prompt_with_scratch_default feeScheduleJson 'Path to JSON'
FEE_SCHEDULE_JSON=$VALUE

TARGET_NODE="$HOST" TARGET_PORT="$PORT" PEM_PASSPHRASE="$PASSPHRASE" \
  java -jar ${TOOLS_PATH}/run/SysFilesUpdate.jar UPDATE \
    "$PAYER" $PEM_FILE "111" $FEE_SCHEDULE_JSON
