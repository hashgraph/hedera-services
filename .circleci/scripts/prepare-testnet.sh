#!/usr/bin/env bash

. ${REPO}/.circleci/scripts/ansible-functions.sh

ansible_prepare | tee -a ${REPO}/test-clients/output/hapi-client.log
