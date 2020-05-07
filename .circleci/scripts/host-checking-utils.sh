#!/usr/bin/env bash

function init_checklist() {
  local N=$1
  local I=0
  HOSTS_CHECKED=''
  while [ $I -lt $N ]; do
    HOSTS_CHECKED="0 $HOSTS_CHECKED"
    I=$((I+1))
  done
  HOSTS_CHECKED=($HOSTS_CHECKED)
}

function are_all_hosts_checked() {
  local I=0
  local N=${#HOSTS_CHECKED[@]}
  local NUM_CHECKED=0
  while [ $I -lt $N ]; do
    if [ "${HOSTS_CHECKED[$I]}" = "1" ]; then
      NUM_CHECKED=$((NUM_CHECKED+1))
    else 
      echo "Host #$I still pending..."
    fi
    I=$((I+1))
  done
  if [ $NUM_CHECKED -ne $N ]; then
    return $((N-NUM_CHECKED))
  fi
  return 0
}

function check_host() {
  local I=0
  local N=${#HOSTS_CHECKED[@]}
  local WHICH=$1
  local NEW_HOSTS_CHECKED=""
  while [ $I -lt $N ]; do
    if [ $I -eq $WHICH ]; then
      NEW_HOSTS_CHECKED="$NEW_HOSTS_CHECKED 1"
    else
      NEW_HOSTS_CHECKED="$NEW_HOSTS_CHECKED ${HOSTS_CHECKED[$I]}"
    fi
    I=$((I+1))
  done
  HOSTS_CHECKED=($NEW_HOSTS_CHECKED)
}
