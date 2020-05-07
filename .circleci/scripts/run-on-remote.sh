#!/usr/bin/env bash

. ${REPO}/.circleci/scripts/utils.sh

if [ $# -lt 2 ]; then
  ci_echo "USAGE: $0 host cmd"
  exit 1
fi

HOST=$1
CMD=$2
echo $(run_on_remote $HOST $CMD)
