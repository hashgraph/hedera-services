#! /bin/sh

STATE_LOC=/Users/neeharikasompalli/Documents/Hedera/mod-testing/replay/round162947998/SignedState.swh
EVENT_STREAMS_LOC=/Users/neeharikasompalli/Documents/Hedera/mod-testing/replay/test-events
DDIR=/Users/neeharikasompalli/Documents/Hedera/Repos/hedera-services/hedera-node/data

rm -rf out/ data/apps data/lib
ln -s $DDIR/apps data/apps
ln -s $DDIR/lib data/lib

./pcli.sh event-stream recover \
  --id=27 \
  --main-name=com.hedera.node.app.ServicesMain \
  -J "-Dhedera.workflows.enabled=" \
  -L "data/lib" -L "data/apps" \
  $STATE_LOC $EVENT_STREAMS_LOC

