#!/bin/bash -x
#


Usage() {
    echo " "
    echo "Usage: <test-config>"
    echo " "
}

env_file=${1}

USER=${2}
PASSWORD=${3}
SERVER=${4}

USERPASSWORD="${USER}:${PASSWORD}"

if [ ! -f "$env_file" ]; then
    echo "Error: a test configuration file is required!"
    Usage
    exit 1
fi

. ${env_file}

# File where web session cookie is saved

COOKIEJAR="$(mktemp)"
CRUMB=$(curl --no-progress-meter -f -u "$USERPASSWORD" --cookie-jar "$COOKIEJAR" "$SERVER/crumbIssuer/api/xml?xpath=concat(//crumbRequestField,%22:%22,//crumb)")

status=$?

if [[ $status -eq 0 ]] ; then

curl --no-progress-meter -f -X POST -u "$USERPASSWORD" --cookie "$COOKIEJAR" -H "$CRUMB" \
    ${SERVER}/job/pipelines/job/nightly/buildWithParameters    \
    -F K8S_CLUSTER="gke_hedera-testing-1_us-central1-a_sandbox-perfnet6"   \
    -F TEST_MAXNODE="${TEST_MAXNODE}"      \
    -F TEST_NETWORK=perfnet6               \
    -F VERSION_SERVICE="${VERSION_SERVICE}"\
    -F VERSION_MIRRORNODE="${VERSION_MIRRORNODE}" \
    -F TEST_LIST="${TEST_LIST}"            \
    -F TEST_ENV_TIME_LOWTPS=2700           \
    -F TEST_SETUP_FILE=ENV.104k.1h \
    -F TEST_TIME="5400" -F TEST_RAMPUP="600" -F TEST_PADDING="600" \
    -F TEST_SUITE_NAME="${TEST_SUITE_NAME}" \
    -F TEST_TAGS="${TEST_TAGS}"

  status=$?
fi
rm "$COOKIEJAR"
exit $status


