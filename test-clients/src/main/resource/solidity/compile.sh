#! /bin/sh
set -x
if [ $# -lt 1 ]; then
  echo "USAGE: $0 [source.sol]"
  exit 1
fi
CONTRACT=${1%%.*}
solcjs --bin --abi -o ../testfiles/ $1 --overwrite
BASE_PATH="../testfiles/${CONTRACT}_sol_${CONTRACT}"
echo $BASE_PATH
mv ${BASE_PATH}.bin ../testfiles/${CONTRACT}.bin
if [ "-abi" = "$2" ]; then
  cat ${BASE_PATH}.abi | python -m json.tool | less
fi
