#!/usr/bin/env bash

. ${REPO}/.circleci/scripts/utils.sh

if [ ! -f "$LOG4J_BKUP_FINGERPRINT" ]; then
  # Log level at INFO per default; back this up
  cp "$CLIENT_RESOURCES/log4j2.xml" "$CLIENT_RESOURCES/bkup-log4j2.xml"
  touch $LOG4J_BKUP_FINGERPRINT

  cp "$CLIENT_RESOURCES/regression-log4j2.xml" "$CLIENT_RESOURCES/log4j2.xml"
  rm -f $CLIENT_RESOURCES_REBUILD_FINGERPRINT
  ci_echo "Set log level to WARN; next test must repackage client resources"
fi
