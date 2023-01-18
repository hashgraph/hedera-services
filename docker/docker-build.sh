#!/usr/bin/env bash

TAG=$(./read-env.sh)
echo "CREATING CONTAINER FOR VERSION "${TAG}

cp Dockerfile ./../
cp .dockerignore ./../
cp .env ./../
$(cd .. && docker build -t "services-node:${TAG}" .)
rm ./../Dockerfile
rm ./../.env
rm ./../.dockerignore