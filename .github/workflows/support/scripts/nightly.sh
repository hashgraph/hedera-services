// SPDX-License-Identifier: Apache-2.0
#!/bin/bash -x
#
# Launch test client for nightly service performance test
Usage() {
    echo " "
    echo "Usage: <test-config>"
    echo " "
}

USER=${1}
PASSWORD=${2}
SERVER=${3}
K8S_CLUSTER=${4}
VERSION_SERVICE=${5}
VERSION_MIRRORNODE=${6}

USERPASSWORD="${USER}:${PASSWORD}"

# File where web session cookie is saved
COOKIEJAR="$(mktemp)"
CRUMB=$(curl --no-progress-meter -f -u "$USERPASSWORD" --cookie-jar "$COOKIEJAR" "$SERVER/crumbIssuer/api/xml?xpath=concat(//crumbRequestField,%22:%22,//crumb)")

status=$?

if [[ $status -eq 0 ]] ; then

curl --no-progress-meter -f -X POST -u "$USERPASSWORD" --cookie "$COOKIEJAR" -H "$CRUMB" \
    ${SERVER}/job/pipelines/job/nightly/buildWithParameters  \
    -F K8S_CLUSTER="${K8S_CLUSTER}"                          \
    -F VERSION_SERVICE="${VERSION_SERVICE}"                  \
    -F VERSION_MIRRORNODE="${VERSION_MIRRORNODE}"

  status=$?
fi
rm "$COOKIEJAR"

echo date "Nightly test started! - status [${status}]"
exit $status

