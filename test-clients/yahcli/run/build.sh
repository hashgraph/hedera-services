#! /bin/sh
if [ $# -lt 1 ]; then
  echo "USAGE: $0 <tag>"
  exit 1
fi

cd ..
mvn clean compile assembly:single@yahcli-jar
cd -
run/refresh-jar.sh

TAG=$1
docker build -t yahcli:$TAG .
