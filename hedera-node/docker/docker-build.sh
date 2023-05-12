#!/usr/bin/env bash

if [[ $# -lt 2 ]]; then
  echo "Usage: ${0} [tag] [project_dir]"
  exit 1
fi

TAG=$(./read-env.sh)
echo "CREATING CONTAINER FOR VERSION ${TAG}"
echo "Using project directory: ${2}"
echo

echo "Copying files to ${2}:"
cp -v Dockerfile "${2}"
cp -v .dockerignore "${2}"
cp -v .env "${2}"
cd "${2}" || exit 67
echo
echo "Building container:"
docker build -t "services-node:${TAG}" . || exit "${?}"
rm -v "${2}/Dockerfile"
rm -v "${2}/.env"
rm -v "${2}/.dockerignore"
