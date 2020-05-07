#!/bin/bash

SYS_FILE_NUM=$1
SYS_FILE_NAME=$2

. ${TOOLS_PATH}/run/functions.sh

prompt_with_scratch_default host "Host of $SYS_FILE_NAME to update"
HOST=$VALUE
prompt_with_scratch_default port 'Port on host'
PORT=$VALUE
prompt_with_scratch_default payer 'Payer account to use'
PAYER=$VALUE
prompt_with_scratch_default b64keypair 'Base64-encoded keypair for payer'
B64KEYPAIR=$VALUE

TARGET_NODE="$HOST" TARGET_PORT="$PORT" \
  java -jar ${TOOLS_PATH}/run/SysFilesUpdate.jar UPDATE \
    "$PAYER" $B64KEYPAIR $SYS_FILE_NUM
