#!/usr/bin/env bash
if [ $# -lt 1 ]; then
  echo "USAGE: $0 cmd-to-trap"
  exit 1
fi

. ${REPO}/.circleci/scripts/terraform-functions.sh

CMD=$1
if [[ "$CMD" != "" && "$TF_WORKSPACE" != "" ]]; then
  echo "TF_WORKSPACE is: $TF_WORKSPACE"
fi
trap tf_cleanup EXIT
eval $CMD
