#! /bin/sh
DEFAULT_TAG=$(git describe --tags --always --dirty)
GIT_TAG=${1:-"$DEFAULT_TAG"}
echo $GIT_TAG
echo "TAG=$GIT_TAG" > .env
echo "REGISTRY_PREFIX=" >> .env
docker build -t services-node:${GIT_TAG} .
