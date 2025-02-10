// SPDX-License-Identifier: Apache-2.0
#! /bin/sh
if [ $# -lt 1 ]; then
  echo "USAGE: $0 <tag>"
  exit 1
fi

TAG=$1
docker push gcr.io/hedera-registry/yahcli:$TAG
