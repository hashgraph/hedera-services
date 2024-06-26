#! /bin/sh
if [ $# -lt 1 ]; then
  echo "USAGE: $0 [contract]"
  exit 1
fi

CONTRACT=${1}
cp ../contracts/${CONTRACT}/*.sol .
./compile.sh ${CONTRACT}.sol
rm ${CONTRACT}.sol
