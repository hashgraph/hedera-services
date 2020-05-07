#!/usr/bin/env bash
if [ $# -lt 1 ]; then
  echo "USAGE: $0 path-to-package"
  exit 1
fi

PACKAGE_DIR=$1
cp -r hedera-node/data/backup $PACKAGE_DIR
cp hedera-node/data/apps/HederaNode.jar $PACKAGE_DIR/HGCApp.jar
cp hedera-node/swirlds.jar $PACKAGE_DIR
