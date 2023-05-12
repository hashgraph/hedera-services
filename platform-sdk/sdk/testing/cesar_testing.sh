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
dayFolder="Results-$startDate"
testFolder="$dayFolder/$startTime"
gitURL="https://$GTA@github.com/swirlds/platform-swirlds.git"

mkdir -p "$logDir"

# Open STDOUT as $LOG_FILE file for read and write.
exec 1> $logFile

# Redirect STDERR to STDOUT
exec 2>> $errFile


POSITIONAL=()
########################
# Default param values #
########################
BRANCH="develop"
COMMIT="HEAD"
TEST="ALL"
#######################################
# Get all CLI arguements and set them #
#######################################
while [[ $# -gt 0 ]]
do
key="$1"

case $key in
    -x|--current)
    CURRENT=true
    shift
    shift
    ;;
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

if [ "$CURRENT" = true ] ; then
  echo Using current branch with no changes. 
else
  echo BRANCH BRANCH  = "${BRANCH}"
  echo COMMIT PATH     = "${COMMIT}"
  echo TEST PATH    = "${TEST}"
  echo DEFAULT         = "${DEFAULT}"
  echo "checking out git branch ${BRANCH} for commit ${COMMIT}, then running ${TEST} test(s)" 
fi
# Change Script Directory to do everything in ~/git/platform-swirlds/ #
cd -- "/home/ubuntu/git/platform-swirlds/"
############################################################################
# Get current branch information in order to go back to programming easily #
############################################################################
if [ "$CURRENT" != true ] ; then
  CURRENT_BRANCH=$(git branch | sed -n '/\* /s///p')
  CURRENT_COMMIT=$(git show |  sed -n '/commit /s///p')
  echo "current branch: ${CURRENT_BRANCH}  current commit: ${CURRENT_COMMIT}"
  GIT_STASH=$(git stash) #stash current changes to make sure the commits is tested not the staged changes *** may want an option to just test current code
  if [ "${GIT_STASH}" = "No local changes to save" ]
  then
	IS_STASHED=false;
  else
	IS_STASHED=true;
  fi
  if [ "${CURRENT_BRANCH}" != "${BRANCH}" ]
  then
############################################################################
# pull latest code, see if a branch by the name already exists on this box #
# If a brnach exists check it out, if not check it out to track changes    #
# against origin/{BRANCH}						   #
############################################################################
	(git pull $gitURL)
	BRANCH_MATCHES=$(git branch -a | grep -c "${BRANCH}$")
        echo "BRANCH_MATCHES = ${BRANCH_MATCHES}"
	TRACK_FLAG=""
	if [ "${BRANCH_MATCHES}" -eq 1 ]
	then
		TRACK_FLAG="--track origin/"
	fi
	(git checkout ${TRACK_FLAG}${BRANCH} )
	(git reset --hard)
	(git pull $gitURL)
  fi
fi

##################
# Main test code #
##################
(mvn dependency:copy-dependencies 1>> "${logDir}/mvn delpoy ${logFile}" 2>> "${logDir}/mvn delpoy ${errFile}")
(mvn -DskipTests=true -X clean deploy  1>> "${logDir}/mvn delpoy ${logFile}" 2>> "${logDir}/mvn delpoy ${errFile}")
wait
if grep -nw "${logDir}/mvn delpoy ${logFile}" -e "FAILURE"; then 
  echo "mvn FAILED" 
  (rclone copy ${gitFilename} ATFRemote:$testFolder -v 1>> "${logDir}/rclone ${logFile}" 2>> "${logDir}/rclone ${errFile}")
  (rclone copy "${logDir}/mvn delpoy ${logFile}" ATFRemote:$testFolder -v 1>> "${logDir}/rclone ${logFile}" 2>> "${logDir}/rclone ${errFile}")
  exit 0
else
  echo "Maven Passed!"
fi
# Change Script Directory to do everything in ~/git/platform-swirlds/ #
cd -- "/home/ubuntu/git/platform-swirlds/sdk/testing/"
mkdir -p "results/$dayFolder"
mkdir -p "results/$testFolder"
(rm *.zip)
(rm *.7z)
(rm git-version-*)

gitVersion="$(git log -n 1 --decorate=short | awk -F ' ' 'NR==1{print substr($2,1,8)}')"
gitFilename="git-version-${gitVersion}.sh"
(git log -n 1 --decorate=short >> $gitFilename)
#(./startAwsInstancesAndRunTests.sh ./multiRegionTests/_nightlyTestStatsExperiments.sh  1>> "${logDir}/stats ${logFile}" 2>> "${logDir}/stats ${errFile}")
# (./startAwsInstancesAndRunTests.sh ./multiRegionTests/_nightlyTestGpuStats.sh  1>> "${logDir}/gpu ${logFile}" 2>> "${logDir}/gpu ${errFile}")
#(./startAwsInstancesAndRunTests.sh ./multiRegionTests/_nightlyTestCpuStats.sh  1>> "${logDir}/cpu ${logFile}" 2>> "${logDir}/cpu ${errFile}")

# (./startAwsInstancesAndRunTests.sh ./multiRegionTests/_nightlyTestStatsExperiments.sh  1>> "${logDir}/stats ${logFile}" 2>> "${logDir}/stats ${errFile}")
# (./startAwsInstancesAndRunTests.sh ./multiRegionTests/_nightlyTestGpuStats.sh  1>> "${logDir}/gpu ${logFile}" 2>> "${logDir}/gpu ${errFile}")
#(./startAwsInstancesAndRunTests.sh ./multiRegionTests/_nightlyTestCpuStats.sh  1>> "${logDir}/cpu ${logFile}" 2>> "${logDir}/cpu ${errFile}")


#(./startAwsInstancesAndRunTests.sh ./multiRegionTests/_nightlyTest100TranSizeStats.sh $testFolder  1>> "${logDir}/cpu 100 ${logFile}" 2>> "${logDir}/cpu 100 ${errFile}")
#sleep 5m
#(./startAwsInstancesAndRunTests.sh ./multiRegionTests/_nightlyTest256TranSizeStats.sh $testFolder  1>> "${logDir}/cpu 256 ${logFile}" 2>> "${logDir}/cpu 256 ${errFile}")
#sleep 5m
#(./startAwsInstancesAndRunTests.sh ./multiRegionTests/_nightlyTest512TranSizeStats.sh $testFolder  1>> "${logDir}/cpu 512 ${logFile}" 2>> "${logDir}/cpu 512 ${errFile}")
#sleep 5m
#(./startAwsInstancesAndRunTests.sh ./multiRegionTests/_nightlyTest1024TranSizeStats.sh $testFolder  1>> "${logDir}/cpu 1024 ${logFile}" 2>> "${logDir}/cpu 1024 ${errFile}")
#sleep 5m
#(./startAwsInstancesAndRunTests.sh ./multiRegionTests/_nightlyTest2048TranSizeStats.sh $testFolder  1>> "${logDir}/cpu 2048 ${logFile}" 2>> "${logDir}/cpu 2048 ${errFile}")
#sleep 5m
#(./startAwsInstancesAndRunTests.sh ./multiRegionTests/_nightlyTest4096TranSizeStats.sh $testFolder  1>> "${logDir}/cpu 4096 ${logFile}" 2>> "${logDir}/cpu 4096 ${errFile}")
#sleep 5m
#(./startAwsInstancesAndRunTests.sh ./multiRegionTests/_nightlyTest6144TranSizeStats.sh $testFolder  1>> "${logDir}/cpu 6144 ${logFile}" 2>> "${logDir}/cpu 6144 ${errFile}")
#sleep 5m

(./startAwsInstancesAndRunTests.sh _FCMFileTest.sh $testFolder FCM1K.json 1>> "${logDir}/platform test - fcm 1k ${logFile}" 2>> "${logDir}/platform test - fcm 1k ${errFile}")
sleep 5m
# (./startAwsInstancesAndRunTests.sh ./multiRegionTests/_nightlyTestPlatformFCM1M.sh $testFolder FCM1M.json 1>> "${logDir}/platform test - fcm 1m ${logFile}" 2>> "${logDir}/platform test - fcm 1m ${errFile}")
# sleep 5m
#(./startAwsInstancesAndRunTests.sh ./multiRegionTests/_nightlyTestPlatformFCMOnly.sh $testFolder FCMOnly.json 1>> "${logDir}/platform test - fcm only ${logFile}" 2>> "${logDir}/platform test - fcm only ${errFile}")
#sleep 5m
#(./startAwsInstancesAndRunTests.sh ./multiRegionTests/_nightlyTestPlatformFCMOnlyV2.sh $testFolder FCMOnly2.json 1>> "${logDir}/platform test - fcm only v2 ${logFile}" 2>> "${logDir}/platform test - fcm only v2 ${errFile}")
#sleep 5m
# (./startAwsInstancesAndRunTests.sh ./multiRegionTests/_nightlyTestPlatformFCFSOnly.sh $testFolder FileOnly.json 1>> "${logDir}/platform test - fcfs only ${logFile}" 2>> "${logDir}/platform test - fcfs only ${errFile}")
# sleep 5m
# (./startAwsInstancesAndRunTests.sh ./multiRegionTests/_nightlyTestPlatformFCFSNarrow.sh $testFolder FileOnlyNarrowDeep.json 1>> "${logDir}/platform test - fcfs narrow ${logFile}" 2>> "${logDir}/platform test - fcfs narrow ${errFile}")
# sleep 5m
# (./startAwsInstancesAndRunTests.sh ./multiRegionTests/_nightlyTestPlatformFCFSWide.sh $testFolder FileOnlyWideShallow.json 1>> "${logDir}/platform test - fcfs wide ${logFile}" 2>> "${logDir}/platform test - fcfs wide ${errFile}")
# sleep 5m
# (./startAwsInstancesAndRunTests.sh ./multiRegionTests/_nightlyTestRestart.sh $testFolder  1>> "${logDir}/restart ${logFile}" 2>> "${logDir}/restart ${errFile}")

(java -Duser.dir=/home/ubuntu/git/platform-swirlds/sdk/testing -jar graphing-cli-0.10.4-BETA.jar  1>> "${logDir}/graphing cli ${logFile}" 2>> "${logDir}/graphing cli ${errFile}")
(./consolidateNodeLogs.sh results/$testFolder swirlds.log)
(./consolidateNodeLogs.sh results/$testFolder output.log)
#(zip -r graphTest graphs)
#(zip -r resultsTest results/$testFolder)
#(rclone copy resultsTest.zip ATFRemote:$testFolder/cesar -v 1>> "${logDir}/rclone ${logFile}" 2>> "${logDir}/rclone ${errFile}")
#(rclone copy graphTest.zip ATFRemote:$testFolder/cesar -v 1>> "${logDir}/rclone-graphs ${logFile}" 2>> "${logDir}/rclone-graphs ${errFile}")
(7z a -r graphTest graphs)
(7z a -r resultsTest results/$testFolder)
(rclone copy resultsTest.7z ATFRemote:$testFolder/cesar -v 1>> "${logDir}/rclone ${logFile}" 2>> "${logDir}/rclone ${errFile}")
(rclone copy graphTest.7z ATFRemote:$testFolder/cesar -v 1>> "${logDir}/rclone-graphs ${logFile}" 2>> "${logDir}/rclone-graphs ${errFile}")
(rclone copy ${gitFilename} ATFRemote:$testFolder/cesar -v 1>> "${logDir}/rclone ${logFile}" 2>> "${logDir}/rclone ${errFile}")
(rclone copy graphs/test-summary.csv ATFRemote:$testFolder/cesar -v 1>> "${logDir}/rclone-graphs ${logFile}" 2>> "${logDir}/rclone-graphs ${errFile}")

oldifs="$IFS"
IFS=$'\n'
#exceptionLog=$(egrep -nilR 'Exception|ERROR' results/$testFolder/*-swirlds.log) && echo "$exceptionLog" > results/$testFolder/Exceptions-swirlds.log
#exceptionLog=$(egrep -nilR 'Exception|ERROR' results/$testFolder/*-output.log) && echo "$exceptionLog" > results/$testFolder/Exceptions-output.log
exceptionLog=$(./countExceptions.sh "results/$testFolder/*-output.log")  && [[ ! -z "$exceptionLog" ]] && echo "$exceptionLog" > results/$testFolder/Exceptions-output.log
exceptionLog=$(./countExceptions.sh "results/$testFolder/*-swirlds.log")  && [[ ! -z "$exceptionLog" ]] && echo "$exceptionLog" > results/$testFolder/Exceptions-swirlds.log
allLogFiles=( `ls results/$testFolder/*.log | sort` )
for log in ${allLogFiles[@]}; do
  (rclone copy ${log} ATFRemote:$testFolder/cesar -v 1>> "${logDir}/rclone-graphs ${logFile}" 2>> "${logDir}/rclone-graphs ${errFile}")
done
IFS="$oldifs"

# (./archive.sh  1>> "${logDir}/archive ${logFile}" 2>> "${logDir}/archive ${errFile}")
#echo "Moving results and graphs to efs"
mv ./graphs/* /mnt/efs/atf/graph-archives/

mv ./results/* /mnt/efs/atf/result-archives/
rm -rf ./graphs/*
rm -rf ./results/*
rm $gitFilename

########################################################
# retore code to previous branch/commit, pop the stash #
########################################################
if [ "$CURRENT" != true ] ; then
  if [ "${CURRENT_BRANCH}" != "${BRANCH}" ]
  then
        (git checkout ${CURRENT_BRANCH})
  fi
  if [ "$IS_STASHED" = "true" ]
  then
	(git stash pop)
  fi
fi
if [[ -n $1 ]]; then
    echo "Last line of file specified as non-opt/last argument:"
    tail -1 "$1"
fi
(cd /home/ubuntu/git/platform-swirlds)
(chown -R ubuntu:ubuntu *)

date=$(date)
echo date >> /tmp/testing.log
echo "$1 $2 $3" >> /tmp/testing.log
