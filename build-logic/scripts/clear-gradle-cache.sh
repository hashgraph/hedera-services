#!/usr/bin/env bash

# Cleans build files in a way that './gradlew clean' can only dream of.

# The location were this script can be found.
SCRIPT_PATH="$( cd -- "$(dirname "$0")" >/dev/null 2>&1 || exit ; pwd -P )"

# Navigate to the root of the project.
{
  cd $SCRIPT_PATH/../..

  rm -rf ~/.gradle

  find . -name .gradle -exec rm -rvf {} \;
  find . -name build -exec rm -rvf {} \;
}
