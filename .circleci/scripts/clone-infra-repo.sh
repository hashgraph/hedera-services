#!/usr/bin/env bash

. ${REPO}/.circleci/scripts/utils.sh

if [ $# -lt 1 ]; then
  echo "USAGE: $0 branch"
  exit 1
fi

if [ -f ${INFRASTRUCTURE_REPO} ]; then
  ci_echo "Infrastructure already cloned from '$1', doing nothing."
else
  BRANCH="$1"
  ci_echo "Using '$BRANCH' of infrastructure repo..."
  cd /
  GIT_SSH_COMMAND="ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no" \
    git clone git@github.com:swirlds${INFRASTRUCTURE_REPO}.git \
      --branch "$BRANCH" \
      --depth 2
  cd $INFRASTRUCTURE_REPO
  git rev-parse HEAD
fi
