#!/usr/bin/env bash

. ${REPO}/.circleci/scripts/utils.sh
. ${REPO}/.circleci/scripts/rationalize-tf-env-vars.sh

download_node_output

APPEND_TO_EXTANT=${1:-false}

if [ "$APPEND_TO_EXTANT" = false ]; then
  rm -rf $STATS_DIR
fi
mkdir -p $STATS_DIR

for HOST in ${TF_HOSTS[@]}; do
  TARGET_DIR="${REPO}/HapiApp2.0/$HOST"
  TARGET_FILE=$(ls -1 $TARGET_DIR/${STATS_CSV_PREFIX}*.csv)
  if [ "$APPEND_TO_EXTANT" = true ]; then
    BASE_FILE=$(basename $TARGET_FILE)
    EXTANT_TARGET=$STATS_DIR/$BASE_FILE
    CURRENT_LC=$(wc -l $EXTANT_TARGET | awk '{ print $1 }')
    ci_echo "Appending to $EXTANT_TARGET ($CURRENT_LC lines so far)..."
    cat $TARGET_FILE | grep 0.00 >> $EXTANT_TARGET
    NEW_LC=$(wc -l $EXTANT_TARGET | awk '{ print $1 }')
    ci_echo "...finished appending to $EXTANT_TARGET ($NEW_LC lines now)."
  else
    cp $TARGET_FILE $STATS_DIR
  fi
done

NUM_STATS_CSVS=$(ls -1 $STATS_DIR/*.csv | wc -l)
if [ $NUM_STATS_CSVS -ne ${#TF_HOSTS[@]} ]; then
  ci_echo "Warning! $NUM_STATS_CSVS .csv files collected, not ${#TF_HOSTS[@]}!"
fi
