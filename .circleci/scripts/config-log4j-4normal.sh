#!/usr/bin/env bash

. ${REPO}/.circleci/scripts/utils.sh

if [ ! -f "$LOG4J_BKUP_FINGERPRINT" ]; then
  # Log level already at INFO
  exit 0
else
  cp "$CLIENT_RESOURCES/bkup-log4j2.xml" "$CLIENT_RESOURCES/log4j2.xml"
  rm -f $CLIENT_RESOURCES_REBUILD_FINGERPRINT
  ci_echo "Set log level to INFO; next test must repackage client resources"
fi
