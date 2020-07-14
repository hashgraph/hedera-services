#!/usr/bin/env bash

set -o pipefail

if [ $# -lt 1 ]; then
  echo "USAGE: $0 cmd-to-trap"
  exit 1
fi

. ${REPO}/.circleci/scripts/utils.sh

CMD=$1
trap report_failure EXIT
eval $CMD
