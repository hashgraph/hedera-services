#!/bin/bash

. ${TOOLS_PATH}/run/functions.sh

prompt_with_scratch_default host 'Source host of fee schedule to edit'
HOST=$VALUE
prompt_with_scratch_default port 'Source port of fee schedule to edit'
PORT=$VALUE

vi "$SCRATCH_PATH/${HOST}_${PORT}-feeSchedule.txt"
