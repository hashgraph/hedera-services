#!/usr/bin/env bash

. /repo/.circleci/scripts/terraform-functions.sh
. /repo/.circleci/scripts/utils.sh


echo "Build new jars and found changed files and copy to test-clients/updateFiles/"


function updateServiceMainJava
{

    # replace a line in ServicesMain.java
    sed -i -e s/'init finished'/'new version jar'/g  ${REPO}/hedera-node/src/main/java/com/hedera/services/ServicesMain.java

    # rebuild jar files and use timestamp to tell which jar files have been updated
    cd ${REPO}/hedera-node
    beforeTime=`date +'%Y-%m-%d %H:%M:%S'`
    sleep 1
    mvn install -DskipTests
    sleep 1
    afterTime=`date +'%Y-%m-%d %H:%M:%S'`

    echo "beforeTime $beforeTime"
    echo "afterTime $afterTime"

    # only copy updated jar files to target directory
    TARGET_DIR=${REPO}/test-clients/updateFiles
    rm -rf $TARGET_DIR
    mkdir -p $TARGET_DIR
    find . -type f -name "*.jar" -newermt "$beforeTime" -exec cp --parents {} $TARGET_DIR \;
    
    cd -

    echo "Update files after build have been copied to $TARGET_DIR"

    ls -ltr $TARGET_DIR

}


cd $TEST_CLIENTS_DIR

updateServiceMainJava
