#!/usr/bin/env bash

# script to be called by platform regression scrip to build new jar for update feature test
#
#
set -eE

updateServiceMainJava()
{
    # rebuild jar files and use timestamp to tell which jar files have been updated
    cd ..
    mvn -T 2C install -DskipTests

    # replace a line in ServicesMain.java
    sed -i -e s/'init finished'/'new version jar'/g  ../hedera-node/src/main/java/com/hedera/services/ServicesMain.java

    beforeTime=`date +'%Y-%m-%d %H:%M:%S'`
    sleep 1
    mvn -T 2C install -DskipTests
    sleep 1
    afterTime=`date +'%Y-%m-%d %H:%M:%S'`

    echo "beforeTime $beforeTime"
    echo "afterTime $afterTime"

    cd -

    cd ../hedera-node

    if [[ -n "${CI}" ]]; then
        echo "Installing rsync"
        sudo apt update; sudo apt --assume-yes install rsync
    fi

    # only copy updated jar files to target directory
    TARGET_DIR=../test-clients/updateFiles
    rm -rf $TARGET_DIR
    mkdir -p $TARGET_DIR
    find ./data -type f -name "*.jar" -newermt "$beforeTime" -exec rsync  {} $TARGET_DIR \;

    if [[ -n "${CI}" ]]; then
        echo "Running on CIRCLECI, no need to restore"
    else
        echo "Restore source code and jar files"
        git checkout ../hedera-node/src/main/java/com/hedera/services/ServicesMain.java

        # rebuild after checkout to recover binary
        mvn -T 2C  install -DskipTests
    fi

    cd -

    echo "Update files after build have been copied to $TARGET_DIR"

    ls -ltr $TARGET_DIR

}

updateServiceMainJava
