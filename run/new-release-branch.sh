#! /bin/sh

if [ $# -lt 2 ]; then
  echo "USAGE: $0 <major.version> <minor.version>"
  exit 1
fi

MAJOR=$1
MINOR=$2
BRANCH_NAME="release/${MAJOR}.${MINOR}"

mvn release:branch -DbranchName=${BRANCH_NAME}
git commit --amend --signoff
git push -f
