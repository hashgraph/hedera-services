#! /bin/sh
if [ $# -lt 1 ]; then
  echo "USAGE: $0 <JSON keyfile>"
  exit 1
fi

KEYFILE_JSON=$1
cat $KEYFILE_JSON | \
  docker login -u _json_key --password-stdin https://gcr.io
