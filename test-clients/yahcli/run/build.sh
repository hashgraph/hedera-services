#! /bin/sh
TAG=${1:-'0.2.6'}

cd ../..
./gradlew shadowJar \
  -PsjJar=yahcli.jar -PsjMainClass=com.hedera.services.yahcli.Yahcli
cd -
run/refresh-jar.sh

docker build -t yahcli:$TAG .
