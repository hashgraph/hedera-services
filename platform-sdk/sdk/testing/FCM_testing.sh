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

# fileBeats Setup
. ~/fbsetup.sh

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

# Change to Script Directory (~/git/platform-swirlds/sdk/testing) to do everything there #
cd "$(dirname $0)"
scriptDir="$(pwd)"

startDate="$(date '+%Y-%m-%d')"
startTime="$(date '+%Y%m%d-%H%M')"
baseFile="test $startTime"
logFile="${baseFile}.txt"
errFile="${baseFile}.err"
logDir="/mnt/efs/atf/logs/logs $startTime"
runName="${startTime}-FCM"
dayFolder="$runName"
nodeName="$runName-$startTime"
testFolder="$dayFolder/file-$startTime"

printf "runName=%s\nnodeName=%s\nstartTime=%s" "$runName" "$nodeName" "$startTime" > runDetails.sh


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
# Change to top level directory of git repo (~/git/platform-swirlds) #
cd "$scriptDir"/../..
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
  
  (rclone copy ${gitFilename} ShareRemote:$testFolder -v 1>> "${logDir}/rclone ${logFile}" 2>> "${logDir}/rclone ${errFile}")
  (rclone copy "${logDir}/mvn delpoy ${logFile}" ShareRemote:$testFolder -v 1>> "${logDir}/rclone ${logFile}" 2>> "${logDir}/rclone ${errFile}")
  exit 0
else
  echo "Maven Passed!"
fi
# Change to Script Directory (~/git/platform-swirlds/sdk/testing) to do everything there #
cd "$scriptDir"
mkdir -p "results/$dayFolder"
mkdir -p "results/$testFolder"
(rm *.zip)
(rm *.7z)
(rm git-version-*)

gitVersion="$(git log -n 1 --decorate=short | awk -F ' ' 'NR==1{print substr($2,1,8)}')"
gitFilename="git-version-${gitVersion}.sh"
(git log -n 1 --decorate=short >> $gitFilename)

#(./startStreamTest.sh ./singleRegionTests/_nightlyTestPlatformFCM1K.sh $testFolder FCM1K.json 1>> "${logDir}/platform test - fcm 1k ${logFile}" 2>> "${logDir}/platform test - fcm 1k ${errFile}")
#sleep 2m
(./startStreamTest.sh ./singleRegionTests/_nightlyTestPlatformFCM1M.sh $testFolder FCM1M.json 1>> "${logDir}/platform test - fcm 1m ${logFile}" 2>> "${logDir}/platform test - fcm 1m ${errFile}")
#sleep 2m
#(./startStreamTest.sh ./singleRegionTests/_nightlyTestPlatformFCMOnly.sh $testFolder FCMOnly.json 1>> "${logDir}/platform test - fcm only ${logFile}" 2>> "${logDir}/platform test - fcm only ${errFile}")
#sleep 2m
#(./startStreamTest.sh ./singleRegionTests/_nightlyTestPlatformFCMOnlyV2.sh $testFolder FCMOnly2.json 1>> "${logDir}/platform test - fcm only v2 ${logFile}" 2>> "${logDir}/platform test - fcm only v2 ${errFile}")
#sleep 2m

(java -Duser.dir=/home/ubuntu/git/platform-swirlds/sdk/testing -Dswirlds.input.results=results/$testFolder -jar graphing-cli-0.10.4-BETA.jar  1>> "${logDir}/graphing cli ${logFile}" 2>> "${logDir}/graphing cli ${errFile}")
(./consolidateNodeLogs.sh results/$testFolder swirlds.log)
(./consolidateNodeLogs.sh results/$testFolder output.log)

(zip -r graphTest graphs)
#due to size constraints for Sharepoint zip does not include steamserver events.
(zip -r resultsTest results/$testFolder -x \*/stream_60051/\* \*/stream_60052/\*)
(zip -r streamingTest results/$testFolder -i \*.evts)
(rclone copy resultsTest.zip ATFRemote:$testFolder -v 1>> "${logDir}/rclone ${logFile}" 2>> "${logDir}/rclone ${errFile}")
(rclone copy graphTest.zip ATFRemote:$testFolder -v 1>> "${logDir}/rclone-graphs ${logFile}" 2>> "${logDir}/rclone-graphs ${errFile}")
(rclone copy streamingTest.zip ATFRemote:$testFolder -v 1>> "${logDir}/rclone-streaming ${logFile}" 2>> "${logDir}/rclone-streaming ${errFile}")
(rclone copy resultsTest.zip ShareRemote:$testFolder -v 1>> "${logDir}/rclone ${logFile}" 2>> "${logDir}/rclone ${errFile}")
(rclone copy graphTest.zip ShareRemote:$testFolder -v 1>> "${logDir}/rclone-graphs ${logFile}" 2>> "${logDir}/rclone-graphs ${errFile}")
(rclone copy streamingTest.zip ShareRemote:$testFolder -v 1>> "${logDir}/rclone-streaming ${logFile}" 2>> "${logDir}/rclone-streaming ${errFile}")
#(7z a -r graphTest graphs)
#(7z a -r resultsTest results/$testFolder)
#(rclone copy resultsTest.7z ATFRemote:$testFolder -v 1>> "${logDir}/rclone ${logFile}" 2>> "${logDir}/rclone ${errFile}")
#(rclone copy graphTest.7z ATFRemote:$testFolder -v 1>> "${logDir}/rclone-graphs ${logFile}" 2>> "${logDir}/rclone-graphs ${errFile}")
(rclone copy ${gitFilename} ATFRemote:$testFolder -v 1>> "${logDir}/rclone ${logFile}" 2>> "${logDir}/rclone ${errFile}")
(rclone copy graphs/test-summary.csv ATFRemote:$testFolder -v 1>> "${logDir}/rclone-graphs ${logFile}" 2>> "${logDir}/rclone-graphs ${errFile}")
(rclone copy ${gitFilename} ShareRemote:$testFolder -v 1>> "${logDir}/rclone ${logFile}" 2>> "${logDir}/rclone ${errFile}")
(rclone copy graphs/test-summary.csv ShareRemote:$testFolder -v 1>> "${logDir}/rclone-graphs ${logFile}" 2>> "${logDir}/rclone-graphs ${errFile}")

oldifs="$IFS"
IFS=$'\n'
#exceptionLog=$(egrep -nilR 'Exception|ERROR' results/$testFolder/*-swirlds.log) && echo "$exceptionLog" > results/$testFolder/Exceptions-swirlds.log
#exceptionLog=$(egrep -nilR 'Exception|ERROR' results/$testFolder/*-output.log) && echo "$exceptionLog" > results/$testFolder/Exceptions-output.log
exceptionLog=$(./countExceptions.sh "results/$testFolder/*-output.log")  && [[ ! -z "$exceptionLog" ]] && echo "$exceptionLog" > results/$testFolder/Exceptions-output.log
exceptionLog=$(./countExceptions.sh "results/$testFolder/*-swirlds.log")  && [[ ! -z "$exceptionLog" ]] && echo "$exceptionLog" > results/$testFolder/Exceptions-swirlds.log
allLogFiles=( `ls results/$testFolder/*.log | sort` )
for log in ${allLogFiles[@]}; do
  (rclone copy ${log} ATFRemote:$testFolder -v 1>> "${logDir}/rclone-graphs ${logFile}" 2>> "${logDir}/rclone-graphs ${errFile}")
  (rclone copy ${log} ShareRemote:$testFolder -v 1>> "${logDir}/rclone-graphs ${logFile}" 2>> "${logDir}/rclone-graphs ${errFile}")
done

#export results to fileBeats server
rm -rf ${tempFolder}
mkdir ${tempFolder}
find ./results -name "*.csv" -exec rsync -R "{}" ${tempFolder} \;
ls ${tempFolder}

expect <<END
   spawn rsync -a -r -z -e "ssh -o StrictHostKeyChecking=no -i ${fbpemfile}" "${tempFolder}/" "${fbuser}@${fbaddress}:${fbFolder}/"
   expect "Enter passphrase for key '${fbpemfile}':"
   send "${fbpass}\r"
   expect eof
END
echo "FILEBEATS rsync folder:"
expect <<END
   spawn ssh -i ${fbpemfile} ${fbuser}@${fbaddress} "ls -la ${fbFolder}"
   expect "Enter passphrase for key '${fbpemfile}':"
   send "${fbpass}\r"
   expect eof
END

rm -rf ${tempFolder}

IFS="$oldifs"

# (./archive.sh  1>> "${logDir}/archive ${logFile}" 2>> "${logDir}/archive ${errFile}")
echo "Moving results and graphs to efs"
mv ./graphs/* /mnt/efs/atf/graph-archives/
mkdir /mnt/efs/atf/result-archives/$dayFolder
mkdir /mnt/efs/atf/result-archives/$testFolder
mv ./results/$testFolder /mnt/efs/atf/result-archives/$testFolder

mv ./graphs/* /mnt/efs/atf/graph-archives/

rm -rf ./graphs/*
rm -rf ./results/$testFolder/*
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
# Change to top level directory of git repo (~/git/platform-swirlds) #
cd "$scriptDir"/../..
(chown -R ubuntu:ubuntu *)

date=$(date)
echo date >> /tmp/testing.log
echo "$1 $2 $3" >> /tmp/testing.log
