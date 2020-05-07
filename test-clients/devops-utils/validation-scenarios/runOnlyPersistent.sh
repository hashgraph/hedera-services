#! /bin/sh
if [ $# -lt 1 ]; then
  echo "USAGE: $0 <target-network> [<comma-separated-scenarios>]"
fi
NETWORK=$1
SCENARIOS=${2:-"crypto,file,contract,consensus"}
java -jar ValidationScenarios.jar \
  "target=$NETWORK" \
  "scenarios=$SCENARIOS" \
  "novel=false" \
  "revocation=false"
