#! /bin/sh

TAG=${1:-'0.1.10'}

docker run -it -v $(pwd):/launch yahcli:$TAG -n localhost -p 2 sysfiles upload throttles
