#!/usr/bin/env bash
#
# Copyright 2016-2022 Hedera Hashgraph, LLC
#
# This software is the confidential and proprietary information of
# Hedera Hashgraph, LLC. ("Confidential Information"). You shall not
# disclose such Confidential Information and shall use it only in
# accordance with the terms of the license agreement you entered into
# with Hedera Hashgraph.
#
# HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
# THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
# TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
# PARTICULAR PURPOSE, OR NON-INFRINGEMENT. HEDERA HASHGRAPH SHALL NOT BE LIABLE FOR
# ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
# DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
#

cd "`dirname "$0"`"

. _config.sh; . _functions.sh;

# choose a test to be run
if [[ -z $1 ]]
  then
    chooseTestToRun
    testConfig=$optionChosen
  else
    testConfig="$1"
fi

# load the configuration
. "$testConfig";

privateAddressesFile="privateAddresses.txt"
publicAddressesFile="publicAddresses.txt"
> "$privateAddressesFile"
> "$publicAddressesFile"
for (( i = 0; i < $totalInstances; i++ )); do
  echo "127.0.0.1" >> "$privateAddressesFile"
  echo "127.0.0.1" >> "$publicAddressesFile"
done
privateAddresses=( `cat "$privateAddressesFile"` )
publicAddresses=( `cat "$publicAddressesFile"` )

testRunningOn="local"

createExperimentConfig "$testConfig" "."

#running test
. "$testScriptToRun";

# clean up when it's done
. cleanUpLogsAndSavedData.sh

# notify the user the test is done
echo -ne '\007'