#!/usr/bin/env bash

# Cleans build files in a way that './gradlew clean' can only dream of.

# The location were this script can be found.
SCRIPT_PATH="$( cd -- "$(dirname "$0")" >/dev/null 2>&1 || exit ; pwd -P )"
cd $SCRIPT_PATH

rm -rf ~/.gradle
rm -rf $(find ../ -name .gradle)
rm -rf $(find ../ -name build)
