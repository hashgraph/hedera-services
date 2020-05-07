#!/bin/bash

. ${TOOLS_PATH}/run/functions.sh

prompt_with_scratch_default host 'Source host of node details to edit'
HOST=$VALUE
prompt_with_scratch_default port 'Source port of node details to edit'
PORT=$VALUE

vi "$SCRATCH_PATH/${HOST}_${PORT}-nodeDetails.json"
