#! /bin/sh
TAG=${1:-'0.2.6'}

cd ../..
./gradlew shadowJar \
  -PsjJar=yahcli.jar -PsjMainClass=com.hedera.services.yahcli.Yahcli
cd -
run/refresh-jar.sh

docker buildx create --use --name multiarch
docker buildx build --push --platform linux/amd64,linux/arm64 -t gcr.io/hedera-registry/yahcli:$TAG .
