#!/usr/bin/env bash

set +e
cd $REPO

CONFIG_FILE_PATH='${REPO}/.circleci/scripts/resources/customMigrationConfig.json'

pip3 install boto
pip3 install boto3
python3 ${REPO}/.circleci/scripts/automateCustomMigration.py  ${CONFIG_FILE_PATH}
