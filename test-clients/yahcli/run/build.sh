#! /bin/sh
TAG=${1:-'0.2.0'}

cd ..
mvn clean compile assembly:single@yahcli-jar
cd -
run/refresh-jar.sh

docker buildx create --use --name multiarch
docker buildx build --push --platform linux/amd64,linux/arm64 -t gcr.io/hedera-registry/yahcli:$TAG .
