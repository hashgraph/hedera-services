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

#########################
# The command line help #
#########################
display_help() {
    echo "Usage: $0 [option...] " >&2
    echo
    echo "   -b, --branch           git branch to run tests on, defaults to master"
    echo "   -c, --commit           git commit to run branch on, defaults to HEAD" 
    echo "   -t, --test             _testXXX*.sh script to run test on, defaults to running all scripts "
    # echo "   -t, --test             Set on which display to host on "
    echo
    # echo some stuff here for the -a or --add-options 
    exit 1
}
startDate="$(date '+%Y-%m-%d')"
startTime="$(date '+%Y-%m-%d-%H-%M')"
baseFile="test $(date '+%Y-%m-%d-%H-%M-%S')"
logFile="${baseFile}.txt"
errFile="${baseFile}.err"
logDir="/mnt/efs/atf/logs/logs $(date '+%Y-%m-%d %H-%M-%S')"
dayFolder="Results-$startDate-CPUBufferTest-5MBPS"
testFolder="$dayFolder/$startTime"

echo "$testFolder"

mkdir -p "$logDir"
mkdir -p "results/$dayFolder"
mkdir -p "results/$testFolder"


# Open STDOUT as $LOG_FILE file for read and write.
exec 1> $logFile

# Redirect STDERR to STDOUT
exec 2> $errFile


POSITIONAL=()
########################
# Default param values #
########################
BRANCH="master"
COMMIT="HEAD"
TEST="ALL"
#######################################
# Get all CLI arguements and set them #
#######################################
while [[ $# -gt 0 ]]
do
key="$1"

case $key in
    -b|--branch)
    BRANCH="$2"
    shift # past argument
    shift # past value
    ;;
    -c|--commit)
    COMMIT="$2"
    shift # past argument
    shift # past value
    ;;
    -t|--test)
    TEST="$2"
    shift # past argument
    shift # past value
    ;;
    -h|--help)
    display_help
    exit 0
    ;;
    --default)
    DEFAULT=YES
    shift # past argument
    ;;
    *)    # unknown option
    POSITIONAL+=("$1") # save it in an array for later
    shift # past argument
    ;;
esac
done
set -- "${POSITIONAL[@]}" # restore positional parameters
################################################
# Print out all CLI Parameters, set of default #
################################################
echo BRANCH BRANCH  = "${BRANCH}"
echo COMMIT PATH     = "${COMMIT}"
echo TEST PATH    = "${TEST}"
echo DEFAULT         = "${DEFAULT}"
echo "checking out git branch ${BRANCH} for commit ${COMMIT}, then running ${TEST} test(s)" 
# Change Script Directory to do everything in ~/git/platform-swirlds/ #
cd -- "/home/ubuntu/git/platform-swirlds/"
############################################################################
# Get current branch information in order to go back to programming easily #
############################################################################
############################################################################
# pull latest code, see if a branch by the name already exists on this box #
# If a brnach exists check it out, if not check it out to track changes    #
# against origin/{BRANCH}						   #
############################################################################
##################
# Main test code #
##################
(mvn dependency:copy-dependencies 1>> "${logDir}/mvn delpoy ${logFile}" 2> "${logDir}/mvn delpoy ${errFile}")
(mvn clean deploy  1>> "${logDir}/mvn delpoy ${logFile}" 2>> "${logDir}/mvn delpoy ${errFile}")

# Change Script Directory to do everything in ~/git/platform-swirlds/ #
cd -- "/home/ubuntu/git/platform-swirlds/sdk/testing/"

(./startAwsInstancesAndRunTests.sh ./cpuCacheTests/_cpuCacheTest40K100BPTVerif.sh $testFolder  1>> "${logDir}/cpu 100BPT 40K ${logFile}" 2> "${logDir}/cpu 100BPT 40K ${errFile}")
#sleep 60s
#(./startAwsInstancesAndRunTests.sh ./cpuCacheTests/_cpuCacheTest50K100BPTVerif.sh $testFolder  1>> "${logDir}/cpu 100BPT 50K ${logFile}" 2> "${logDir}/cpu 100BPT 50K ${errFile}")
#sleep 60s
#(./startAwsInstancesAndRunTests.sh ./cpuCacheTests/_cpuCacheTest60K100BPTVerif.sh $testFolder  1>> "${logDir}/cpu 100BPT 60K ${logFile}" 2> "${logDir}/cpu 100BPT 60K ${errFile}")
#sleep 60s
#(./startAwsInstancesAndRunTests.sh ./cpuCacheTests/_cpuCacheTest40K128BPTVerif.sh $testFolder  1>> "${logDir}/cpu 128BPT 40K ${logFile}" 2> "${logDir}/cpu 128BPT 40K ${errFile}")
#sleep 60s
#(./startAwsInstancesAndRunTests.sh ./cpuCacheTests/_cpuCacheTest50K128BPTVerif.sh $testFolder  1>> "${logDir}/cpu 128BPT 50K ${logFile}" 2> "${logDir}/cpu 128BPT 50K ${errFile}")
#sleep 60s
#(./startAwsInstancesAndRunTests.sh ./cpuCacheTests/_cpuCacheTest60K128BPTVerif.sh $testFolder  1>> "${logDir}/cpu 128BPT 60K ${logFile}" 2> "${logDir}/cpu 128BPT 60K ${errFile}")
#sleep 60s
#(./startAwsInstancesAndRunTests.sh ./cpuCacheTests/_cpuCacheTest40K256BPTVerif.sh $testFolder  1>> "${logDir}/cpu 256BPT 40K ${logFile}" 2> "${logDir}/cpu 256BPT 40K ${errFile}")
#sleep 60s
#(./startAwsInstancesAndRunTests.sh ./cpuCacheTests/_cpuCacheTest50K256BPTVerif.sh $testFolder  1>> "${logDir}/cpu 256BPT 50K ${logFile}" 2> "${logDir}/cpu 256BPT 50K ${errFile}")
#sleep 60s
#(./startAwsInstancesAndRunTests.sh ./cpuCacheTests/_cpuCacheTest60K256BPTVerif.sh $testFolder  1>> "${logDir}/cpu 256BPT 60K ${logFile}" 2> "${logDir}/cpu 256BPT 60K ${errFile}")

(java -Duser.dir=/home/ubuntu/git/platform-swirlds/sdk/testing -Dswirlds.diagnostic.graphs.enabled=true -jar graphing-cli-0.9.6-BETA.jar  1>> "${logDir}/graphing cli ${logFile}" 2> "${logDir}/graphing cli ${errFile}")
(zip -r graphTest graphs)
(zip -r resultsTest results/$testFolder)
(rclone copy resultsTest.zip ATFRemote:$dayFolder/$testFolder -v 1>> "${logDir}/rclone ${logFile}" 2> "${logDir}/rclone ${errFile}")
(rclone copy graphTest.zip ATFRemote:$dayFolder/$testFolder/graphs -v 1>> "${logDir}/rclone-graphs ${logFile}" 2> "${logDir}/rclone-graphs ${errFile}")
(rclone copy graphs/test-summary.csv ATFRemote:$dayFolder/$testFolder/graphs -v 1>> "${logDir}/rclone-graphs ${logFile}" 2> "${logDir}/rclone-graphs ${errFile}")
# (./archive.sh  1>> "${logDir}/archive ${logFile}" 2> "${logDir}/archive ${errFile}")
########################################################
# retore code to previous branch/commit, pop the stash #
########################################################

date=$(date)
echo $date >> /tmp/testing.log
echo "$1 $2 $3" >> /tmp/testing.log
