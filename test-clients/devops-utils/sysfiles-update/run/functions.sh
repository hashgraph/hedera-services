#!/bin/bash

prompt_with_scratch_default() {
  ITEM=$1
  VALUE_PROMPT="$2"
  DEFAULT_FILE="$SCRATCH_PATH/last-used-${ITEM}.txt"
  DEFAULT_VALUE=''
  if [ -f "$SCRATCH_PATH/last-used-${ITEM}.txt" ]; then
    DEFAULT_VALUE=$(cat $DEFAULT_FILE)
    if [ "$ITEM" = "passphrase" ]; then
      DEFAULT_REPR="*******"
    elif [ "$ITEM" = "rekey-passphrase" ]; then
      DEFAULT_REPR="*******"
    else
      DEFAULT_REPR="$DEFAULT_VALUE"
    fi
    VALUE_PROMPT="$VALUE_PROMPT ($DEFAULT_REPR)? "
  else
    VALUE_PROMPT="${VALUE_PROMPT}? "
  fi
  if [ "$ITEM" = "passphrase" ]; then
    read -s -p "$VALUE_PROMPT" VALUE ; echo
  elif [ "$ITEM" = "rekey-passphrase" ]; then
    read -s -p "$VALUE_PROMPT" VALUE ; echo
  else
    read -p "$VALUE_PROMPT" VALUE
  fi
  if [ -z "$VALUE" ]; then
    VALUE=$DEFAULT_VALUE
  fi
  echo "$VALUE" > $DEFAULT_FILE
}
