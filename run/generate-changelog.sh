#! /bin/sh

if [ $# -lt 1 ]; then
  echo "USAGE: $0 <milestone>"
  exit 1
fi

MILESTONE="Hedera $1"
CHANGELOG="/docs/changelog-${1}.md"

docker run \
  -v $(PWD)/docs:/docs \
  -v $(PWD)/run:/run \
  springio/github-changelog-generator:0.0.5-SNAPSHOT \
    /bin/sh -c "cd run && java -jar /github-changelog-generator.jar \
    \"$MILESTONE\" $CHANGELOG"
