#! /bin/sh
GIT_TAG=$(git describe --tags --always --dirty)
echo "TAG=$GIT_TAG" > .env
echo "REGISTRY_PREFIX=" >> .env
docker build -t services-node:${GIT_TAG} .
