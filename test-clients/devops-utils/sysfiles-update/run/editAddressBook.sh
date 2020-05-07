#!/bin/bash

. ${TOOLS_PATH}/run/functions.sh

prompt_with_scratch_default host 'Source host of address book to edit'
HOST=$VALUE
prompt_with_scratch_default port 'Source port of address book to edit'
PORT=$VALUE

vi "$SCRATCH_PATH/${HOST}_${PORT}-addressBook.json"
