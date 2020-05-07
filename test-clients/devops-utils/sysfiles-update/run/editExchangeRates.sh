#!/bin/bash

. ${TOOLS_PATH}/run/functions.sh

prompt_with_scratch_default host 'Source host of exchange rates to edit'
HOST=$VALUE
prompt_with_scratch_default port 'Source port of exchanges rates to edit'
PORT=$VALUE

vi "$SCRATCH_PATH/${HOST}_${PORT}-exchangeRates.json"
