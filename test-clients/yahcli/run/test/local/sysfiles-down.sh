#! /bin/sh

TAG=${1:-'0.1.4'}

docker run -v $(pwd):/launch yahcli:$TAG -p 2 sysfiles download all
