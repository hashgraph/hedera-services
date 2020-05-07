#!/usr/bin/env bash

rm -f exec.log

echo `ps -ef | grep Browser` >> exec.log

platform='unknown'
unamestr=`uname`

# find PID
processId=$(ps -ef | grep 'com.swirlds.platform.Browser' | grep -v 'grep' | awk '{ printf $2 }')
echo "HGCApp processID=$processId" >> exec.log

set >> exec.log

source ~/.bash_profile

# detect current platform and kill java process
if [[ "$unamestr" == 'Linux' ]]; then
    if [[ -n "${CI_AWS}" ]]; then
        echo "Running on CIRCLECI" >> exec.log
        platform='circleci'

        FILE="data/apps/HederaNode.jar"
        RENAME="data/apps/HGCApp.jar"
        # if new files contain HederaNode.jar, rename it to HGCApp.jar
        if [ -f $FILE ]; then
            echo "The file HGCApp.jar pre-exist, need rename."
            rm $RENAME
            mv $FILE $RENAME
        fi

        sudo service hgcapp restart >> exec.log 2>&1
    else
        echo "Running on Linux" >> exec.log
        platform='linux'
        kill $processId
    fi
elif [[ "$unamestr" == 'Darwin' ]]; then
    echo "Running on macOS" >> exec.log
    platform='macOS'
    kill $processId
fi

echo "platform=$platform" >> exec.log

# wait for browser terminate

echo "Sleep to wait HGCApp quit" >> exec.log

sleep 15

echo `ps -ef | grep Browser` >> exec.log

echo "Sleep finished" >> exec.log

if [[ $platform == 'linux' ]]; then
    echo "Restart for Linux" >> exec.log
    java -Dflag=1 -cp swirlds.jar:data/lib/* com.swirlds.platform.Browser
elif [[ $platform == 'macOS' ]]; then
    echo "Restart for macOS" >> exec.log
    java -Dflag=1 -cp swirlds.jar:data/lib/* com.swirlds.platform.Browser
elif [[ $platform == 'circleci' ]]; then
    echo "Restart for CircleCI" >> exec.log
else
    echo "Someting wrong" >> exec.log
fi
