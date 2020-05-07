#!/usr/bin/env bash
if [ $# -lt 3 ]; then
  echo "USAGE: $0 host-index node-account failure-line-pattern"
  exit 1
fi

. ${REPO}/.circleci/scripts/rationalize-tf-env-vars.sh

NODE=${TF_HOSTS[$1]}
NODE_ACCOUNT=$(echo $2 | tr -d ' ')
FAILURE_LINE_PATTERN=$(eval $3)

ci_echo "About to run freeze..."
LOG_FILE="$TEST_CLIENTS_DIR/freezeRun.log"
cd $TEST_CLIENTS_DIR
mvn -q exec:java \
    -Dexec.mainClass=com.hedera.services.legacy.regression.umbrella.FreezeServiceTest \
    -Dexec.args="$NODE $NODE_ACCOUNT" \
    -Dexec.cleanupDaemonThreads=false >$LOG_FILE 2>&1 
cat $LOG_FILE
grep -e "$FAILURE_LINE_PATTERN" $LOG_FILE
if [ $? -eq 0 ]; then
  cat $LOG_FILE
  ci_echo "Freeze test failed! (See log above.)"
  exit 1
fi
DIR="$TEST_CLIENTS_DIR/freezeTimeData"
mkdir -p $DIR
date '+%H:%M' > "$DIR/minBeforeExpectedStart.txt"
FREEZE_TXN_BODY=$(grep -A4 "freeze: FreezeTransactionBody =" $LOG_FILE)
ci_echo "$FREEZE_TXN_BODY"
BODY_DATA=(${FREEZE_TXN_BODY//:/ })
printf "%02d" ${BODY_DATA[4]} > "$DIR/hrOfExpectedStart.txt"
printf "%02d" ${BODY_DATA[6]} > "$DIR/minOfExpectedStart.txt"
printf "%02d" ${BODY_DATA[8]} > "$DIR/hrOfExpectedEnd.txt"
printf "%02d" ${BODY_DATA[10]} > "$DIR/minOfExpectedEnd.txt"
cd $DIR
for FILE in $(ls -1); do
  ci_echo "$FILE: $(cat $FILE)"
done
