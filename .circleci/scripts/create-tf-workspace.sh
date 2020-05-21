#!/usr/bin/env bash

NUM_HOSTS=${1:-4}
LIVENESS_TIMEOUT_SECS=${2:-120}
VAR_FILE=${3:-'ci.tfvars'}
AWS_REGION=${4:-'us-east-1'}
AWS_AMI_ID=${5:-''}
echo $AWS_AMI_ID

. ${REPO}/.circleci/scripts/terraform-functions.sh

tf_provision $NUM_HOSTS $LIVENESS_TIMEOUT_SECS $VAR_FILE $AWS_REGION $AWS_AMI_ID
