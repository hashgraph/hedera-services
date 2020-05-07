#!/usr/bin/env bash

. ${REPO}/.circleci/scripts/utils.sh
. ${REPO}/.circleci/scripts/rationalize-tf-env-vars.sh

verify_binary_object_count $1
