#!/usr/bin/env bash
nodeReady=1
echo "Waiting for node to finish initializing..."
until [ $nodeReady -eq 0 ]
do
  grep 'Now current platform status = ACTIVE' ../../compose-network/node0/output/hgcaa.log
  nodeReady=$?
  sleep 3
done
