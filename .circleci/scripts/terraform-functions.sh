#!/usr/bin/env bash

. ${REPO}/.circleci/scripts/rationalize-tf-env-vars.sh

function tf_cleanup {
  SIG=$?
  spotnet="(test-[0-9]{1,8}$)"
  if [[ $SIG -ne 0 && $SIG -ne 137 && $SIG -ne 265 ]]; then
    if [ ${#TF_HOSTS[@]} -gt 0 ]; then
      set +e
      download_node_output
      for HOST in ${TF_HOSTS[@]}; do
        TARGET_DIR="${REPO}/HapiApp2.0/$HOST/output"
        TGT_LOG="$TARGET_DIR/swirlds.log"
        if [ -f $TGT_LOG ]; then
          ci_echo "=== $TGT_LOG ==="
          cat $TGT_LOG | grep -v "ERROR 221  VerificationProvider" | tail -n 100
          ci_echo "================"
        fi
        TGT_LOG="$TARGET_DIR/hgcaa.log"
        if [ -f $TGT_LOG ]; then
          ci_echo "=== $TGT_LOG ==="
          grep "StorageMap roothash on" $TGT_LOG
          ci_echo "================"
          cat $TGT_LOG 
          ci_echo "================"
        fi
      done

      mkdir -p ${REPO}/diagnostics
      python3 ${REPO}/.circleci/scripts/diagnose-logs.py \
        ${REPO}/HapiApp2.0 ${REPO}/diagnostics ${REPO}/.circleci/scripts/resources

      ${REPO}/.circleci/scripts/call-svcs-app-slack.sh \
        -t ${REPO}/diagnostics/slack_msg.txt
      if [ -f ${REPO}/diagnostics/shouldUploadFilteredLogs ]; then
        DIAGNOSTICS_DIR=${REPO}/diagnostics/filtered-logs
        mkdir $DIAGNOSTICS_DIR
        for HOST in ${TF_HOSTS[@]}; do
          RAW_DIR="${REPO}/HapiApp2.0/$HOST/output"
          FILTERED_DIR="$DIAGNOSTICS_DIR/$HOST"
          mkdir $FILTERED_DIR

          for NAME in swirlds hgcaa; do
            TGT_LOG="$RAW_DIR/${NAME}.log"
            ci_echo "Copying filtered version of ${TGT_LOG}..."
            if [ -f "$TGT_LOG" ]; then
              cp "$RAW_DIR/${NAME}-filtered.log" "$FILTERED_DIR"
            fi
          done
          ls -l $FILTERED_DIR
        done
        tar -zcvf ${REPO}/diagnostics/logs-${CIRCLE_BUILD_NUM}.tgz \
          ${REPO}/diagnostics/filtered-logs

        ${REPO}/.circleci/scripts/call-svcs-app-slack.sh \
          -n logs-${CIRCLE_BUILD_NUM}.tgz \
          -f ${REPO}/diagnostics/logs-${CIRCLE_BUILD_NUM}.tgz
      fi

      summarize_postgresql_status
      set -e
    fi

    echo "TF_WORKSPACE is: $TF_WORKSPACE"
    if [[ $TF_WORKSPACE =~ $spotnet ]]; then
      ci_echo "$TF_WORKSPACE is spot net, destroy the workspace..."
      tf_destroy
    else
      ci_echo "$TF_WORKSPACE is manually provisioned, nothing to clean up."
    fi
  fi

  if [[ $SIG -eq 137 || $SIG -eq 265 ]]; then
    ci_echo "This seems to be killed by CircleCi due to its capacity limitation."
    if [[ $TF_WORKSPACE =~ $spotnet ]]; then
      ci_echo "This workflow is running in spot network. No need to continue with rest of it if it fails here."
      exit $SIG
    else
      ci_echo "But since we are running regression with many equivalent clients, no need to fail the whole test if one client get killed by CircleCi"
      exit 0
    fi
  fi

  exit $SIG
}

function wait_for_live_hosts {
  PORT=$1
  TIMEOUT_SECS=${2:-60}
  USE_EXIT_ON_TIMEOUT=${3:-'YES'}
  for TF_HOST in ${TF_HOSTS[@]}; do
    ci_echo "$TF_HOST:$PORT"
    wait-for-it $TF_HOST:$PORT -t $TIMEOUT_SECS
    RC=$?
    if [ $RC -eq 0 ]; then
      ci_echo "$TF_HOST:$PORT is available."
    else
      ci_echo "$TF_HOST:$PORT is unavailable, exiting!"
      if [ "$USE_EXIT_ON_TIMEOUT" = 'YES' ]; then
        exit $RC
      else
        return $RC
      fi
    fi
  done
}

function tf_provision {
  local NUM_NODES=$1
  local TIMEOUT_SECS=$2
  local VAR_FILE=$3
  local AWS_REGION=$4
  local AWS_AMI_ID=$5

  cd $TF_DIR
  ci_echo "Creating '$TF_WORKSPACE' ($VAR_FILE) with $NUM_NODES hosts..."
  echo $VAR_FILE > $VAR_FILE_MEMORY_PATH
  terraform init
  terraform workspace select $TF_WORKSPACE || \
    terraform workspace new $TF_WORKSPACE
  terraform apply -auto-approve \
        -var-file $VAR_FILE \
        -var node_count=$NUM_NODES \
        -var region=$AWS_REGION \
        -var ami_id=$AWS_AMI_ID
  set_tf_hosts_list
  echo $AWS_REGION > ${REPO}/.circleci/aws-region.used
  wait_for_live_hosts 22 $TIMEOUT_SECS
  ci_echo "...finished creating '$TF_WORKSPACE' with $NUM_NODES hosts!"
}

function tf_destroy {
  local FOOTPRINT_PATH=${REPO}/.circleci/tf-destroy.done
  if [ ! -f $FOOTPRINT_PATH ]; then
    local VAR_FILE=$1
    if [ -z "$VAR_FILE" ]; then
      VAR_FILE=$(cat $VAR_FILE_MEMORY_PATH)
    fi
    cd $TF_DIR
    if [[ -z $USE_EXISTING_NETWORK ]]; then

      echo ">>>> [CI] >> Destroying ($VAR_FILE) with hosts ${TF_HOSTS[@]}..."
      TF_WORKSPACE=$(ls -1 $TF_DIR/nets | grep test)
      ci_echo "Current workspace : $TF_WORKSPACE"
      NUM_NODES=${#TF_HOSTS[@]}
      local AWS_REGION=$(cat ${REPO}/.circleci/aws-region.used)
      echo $AWS_REGION
      terraform destroy -auto-approve \
          -var-file "$VAR_FILE" \
          -var node_count=$NUM_NODES \
          -var region=$AWS_REGION \
            && terraform workspace select default \
            && terraform workspace delete $TF_WORKSPACE

      touch $FOOTPRINT_PATH
      ci_echo "...finished destroying workspace!"
    else
      ci_echo "We are re-using existing testnet, do not destroy the testnet."
    fi

  fi
}
