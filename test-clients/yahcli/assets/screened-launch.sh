#! /bin/sh
java -jar /opt/bin/yahcli.jar "$@" 2>syserr.log
RC=$?
if [ ! $RC -eq 0 ]; then
  cat syserr.log
fi
exit $RC
