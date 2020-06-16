#!/usr/bin/env bash

set -eE

unamestr=`uname`

OUTPUT=output/hgcaa.log

#
#  Generate log message to follow log4j2 format
#
#  $0 line number
#  $1 script name
#  $2 message
#
shell_echo() {
    if [[ "$unamestr" == 'Linux' ]]; then
        echo $(date +"%Y-%m-%d %T.%3N") INFO $@ >> $OUTPUT
    elif [[ "$unamestr" == 'Darwin' ]]; then
        echo $(date +"%Y-%m-%d %T") INFO $@ >> $OUTPUT
    else
        echo $@
    fi
}

# make sure output file log exist otherwise
# cannot continue

if [[ -f $OUTPUT ]]; then
    shell_echo $LINENO $0 "$OUTPUT exists."
else
    echo "ERROR: output $OUTPUT does not exist." >> error.log
    exit
fi

shell_echo $LINENO $0 "Start backgorund bash script to update jar files"
shell_echo $LINENO $0 "current user is" `whoami`

# find PID
processId=$(ps -ef | grep 'com.swirlds.platform.Browser' | grep -v 'grep' | awk '{ printf $2 }')
shell_echo $LINENO $0 "HGCApp processID=$processId"


# detect current platform and restart java process
if [[ "$unamestr" == 'Linux' ]]; then
    # useful set circle ci AWS environment variable
    source ~/.bash_profile
    if [[ -n "${CI_AWS}" ]]; then
        shell_echo $LINENO $0 "Running on CIRCLECI"

        FILE="data/apps/HederaNode.jar"
        RENAME="data/apps/HGCApp.jar"
        # if new files contain HederaNode.jar, rename it to HGCApp.jar
        if [ -f $FILE ]; then
            shell_echo $LINENO $0 "The file HGCApp.jar pre-exist, need rename to $FILE."
            rm $RENAME
            mv $FILE $RENAME
        fi

        # call DevOps script here ?
        shell_echo $LINENO $0 "Restart HGCAPP service"
        sudo service hgcapp restart >> $OUTPUT 2>&1

    else
        shell_echo $LINENO $0 "Running on Linux"
        kill $processId

        shell_echo $LINENO $0 "Wait for HGCApp to quit"
        sleep 15

        shell_echo $LINENO $0 "Restart HGCApp"
        java -Dflag=1 -cp swirlds.jar:data/lib/* com.swirlds.platform.Browser

    fi
elif [[ "$unamestr" == 'Darwin' ]]; then
    shell_echo $LINENO $0 "Running on macOS"
    kill $processId

    shell_echo $LINENO $0 "Wait for HGCApp to quit"
    sleep 15

    shell_echo $LINENO $0 "Restart HGCApp"
    java -Dflag=1 -cp swirlds.jar:data/lib/* com.swirlds.platform.Browser
else
    shell_echo $LINENO $0 " untested OS :$platform"
    exit
fi


