#! /bin/sh

STATE_LOC=/Users/neeharikasompalli/Documents/Hedera/mod-testing/replay/round162947998/SignedState.swh
EVENT_STREAMS_LOC=/Users/neeharikasompalli/Documents/Hedera/mod-testing/replay/test-events
DDIR=/Users/neeharikasompalli/Documents/Hedera/Repos/hedera-services/hedera-node/data

rm -rf out/ data/apps data/lib hedera-node/data/recordStreams
ln -s $DDIR/apps data/apps
ln -s $DDIR/lib data/lib

./pcli.sh event-stream recover \
  --id=27 \
  --main-name=com.hedera.node.app.ServicesMain \
  -L "data/lib" -L "data/apps" \
  -J "-Dhedera.workflows.enabled=true" \
  -J "-Dhedera.recordStream.logDir=" \
  -J "-DdumpCheckpoints=MONO_PRE_MIGRATION,MOD_POST_MIGRATION" \
  -J "-Xms30g" \
  $STATE_LOC $EVENT_STREAMS_LOC
