#!/usr/bin/env bash

set +e
cd $REPO

CONFIG_FILE_PATH='${REPO}/.circleci/scripts/resources/customMigrationConfig.json'

python3 ${REPO}/.circleci/scripts/automateCustomMigration.py  ${CONFIG_FILE_PATH}
