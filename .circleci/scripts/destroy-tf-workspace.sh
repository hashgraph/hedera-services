#!/usr/bin/env bash
if [ $# -lt 1 ]; then
  echo "USAGE: $0 var-file"
  exit 1
fi

. ${REPO}/.circleci/scripts/terraform-functions.sh

VAR_FILE=$1
tf_destroy $VAR_FILE
