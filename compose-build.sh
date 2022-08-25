#!/usr/bin/env bash
DEFAULT_TAG=$(./gradlew :hedera-node:showVersion --quiet | tr -d '[:space:]')
GIT_TAG=${1:-"$DEFAULT_TAG"}
echo $GIT_TAG
echo "TAG=$GIT_TAG" > .env
echo "REGISTRY_PREFIX=" >> .env
docker build -t "services-node:${GIT_TAG}" .
