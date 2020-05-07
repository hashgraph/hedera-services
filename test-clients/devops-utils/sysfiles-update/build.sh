#!/usr/bin/env bash

cp ../../target/SysFilesUpdate.jar run/

chmod +x run/*.sh
for FILE in $(ls -1 run/*.sh); do dos2unix $FILE ; done
dos2unix aliases.sh

docker build -t svctools .
