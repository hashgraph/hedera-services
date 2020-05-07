#!/usr/bin/env bash

. ${REPO}/.circleci/scripts/utils.sh

DIR="$TEST_CLIENTS_DIR/freezeTimeData"

THAW_HR="$(cat $DIR/hrOfExpectedEnd.txt)"
THAW_MIN="$(cat $DIR/minOfExpectedEnd.txt)"
echo "$THAW_HR:$THAW_MIN:0.*current platform status = ACTIVE"
