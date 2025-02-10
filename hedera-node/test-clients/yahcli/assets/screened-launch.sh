// SPDX-License-Identifier: Apache-2.0
#! /bin/sh
java -jar /opt/bin/yahcli.jar "$@" 2>syserr.log
RC=$?
cat syserr.log
exit $RC
