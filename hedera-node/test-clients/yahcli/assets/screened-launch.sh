#! /bin/sh
java -jar /opt/bin/yahcli.jar "$@" 2>syserr.log \
  | grep -v sun.reflect.Reflection.getCallerClass
RC=$?
  cat syserr.log
exit $RC
