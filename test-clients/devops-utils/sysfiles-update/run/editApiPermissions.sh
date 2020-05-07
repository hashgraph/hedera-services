#!/bin/bash

. ${TOOLS_PATH}/run/functions.sh

prompt_with_scratch_default host 'Source host of permissions to edit'
HOST=$VALUE
prompt_with_scratch_default port 'Source port of permissions to edit'
PORT=$VALUE

vi "$SCRATCH_PATH/${HOST}_${PORT}-api-permission.properties"
