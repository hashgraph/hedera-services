#!/usr/bin/env bash

# This scripts create a '.env' file that is used for docker & docker-compose as an input of environment variables.
# This script is called by gradle and get the curret project version as an input param

echo "TAG=$1" > .env
echo "REGISTRY_PREFIX=" >> .env
echo "VERSION/TAG UPDATED TO $1"