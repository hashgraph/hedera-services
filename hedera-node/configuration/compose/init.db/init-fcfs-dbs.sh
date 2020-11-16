#! /bin/sh
set -e

SCRATCH='/tmp/cmds.sql'
for DB_NUM in 0 1 2; do
  echo "CREATE DATABASE fcfs${DB_NUM} ;" > $SCRATCH
  echo "GRANT ALL PRIVILEGES ON DATABASE fcfs${DB_NUM} TO swirlds ;" >> $SCRATCH

  cat $SCRATCH
  psql -v ON_ERROR_STOP=1 \
    --username "$POSTGRES_USER" \
    --dbname "$POSTGRES_DB" \
    --file $SCRATCH
done
