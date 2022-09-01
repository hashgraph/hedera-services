#! /bin/sh
java -jar /opt/bin/yahcli.jar "$@" 2>syserr.log \
  | grep -v sun.reflect.Reflection.getCallerClass
RC=$?
if [ ! $RC -eq 0 ]; then
  cat syserr.log
fi
exit $RC
