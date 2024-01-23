#! /bin/sh

if [ $# -lt 2 ]; then
  echo "USAGE: $0 <expected-streams-loc> <actual-streams-loc> [diff-loc]"
  exit 1
fi

java -jar rcdiff.jar -e $1 -a $2 
