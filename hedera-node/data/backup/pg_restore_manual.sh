#!/usr/bin/env bash

#### Script Info ####
SCRIPT_NAME="`basename "${BASH_SOURCE[0]}"`"
SCRIPT_PATH="$( cd "$(dirname "${BASH_SOURCE[0]}")" && pwd )"

#### Configuration ####

# Path where the backup should be stored - this can be local or any mounted NFS or other remote filesystem
# S3 or GCP Buckets can be mounted using s3fs-fuse or gcp-fuse drivers
CFG_STORAGE_PATH=""

# The file name of the tar file containing the backup
CFG_FILE_NAME="PostgresBackup.tar"

# If set to 1 then the CFG_STORAGE_PATH is ignored and is instead computed
CFG_STORE_WITH_STATE=1


#### Standard Exit Codes ####
# These have been taken from sysexits.h

EX_OK=0				# successful termination
EX_USAGE=64			# command line usage error
EX_DATAERR=65 		# data format error

EX_NOINPUT=66		# cannot open input
EX_NOUSER=67		# addressee unknown
EX_NOHOST=68		# host name unknown
EX_UNAVAILABLE=69 	# service unavailable
EX_SOFTWARE=70		# internal software error
EX_OSERR=71			# system error (e.g., can't fork)
EX_OSFILE=72		# critical OS file missing
EX_CANTCREAT=73		# can't create (user) output file
EX_IOERR=74			# input/output error
EX_TEMPFAIL=75		# temp failure; user is invited to retry
EX_PROTOCOL=76		# remote error in protocol
EX_NOPERM=77		# permission denied
EX_CONFIG=78		# configuration error




#### Arguments & Variables ####
#
#	$1 - Path to backup file


ARG_DB_HOSTNAME="localhost"
ARG_DB_PORT="5432"
ARG_DB_USERNAME="swirlds"
ARG_DB_PASSWORD="password"
ARG_DB_CATALOG="fcfs"

ARG_GZ_BACKUP_PATH="$1"

#### Usage ####

usage() {
	echo "Usage: ${SCRIPT_NAME} <postgres backup path>"
	exit $EX_USAGE
};

if [[ "$#" -lt 1 ]]; then
	usage
fi

if [[ -z "${ARG_GZ_BACKUP_PATH}" ]]; then
	usage
fi

#### Command Alias Definitions ####
# Uses /usr/bin/env to resolve the path to the programs

PG_RESTORE_CMD="/usr/bin/env pg_restore"
PSQL_CMD="/usr/bin/env psql"

CP_CMD="/usr/bin/env cp"
MKTEMP_CMD="/usr/bin/env mktemp"
GUNZIP_CMD="/usr/bin/env gunzip"
RM_CMD="/usr/bin/env rm"

#### Program Execution

GZ_BACKUP_FILE="${ARG_GZ_BACKUP_PATH}"

TMP_FOLDER="$(${MKTEMP_CMD} -d)"

if [[ ! -d "${TMP_FOLDER}" ]]; then
  exit $EX_CANTCREAT
fi

$CP_CMD "${GZ_BACKUP_FILE}" "${TMP_FOLDER}"
CP_EX_CODE=$?

if [[ "${CP_EX_CODE}" -ne $EX_OK ]]; then
	echo "cp exited with error code: ${CP_EX_CODE}"
	exit $CP_EX_CODE
fi

TMP_BACKUP_FILE="${TMP_FOLDER}/${CFG_FILE_NAME}"
TMP_GZ_BACKUP_FILE="${TMP_BACKUP_FILE}.gz"

$GUNZIP_CMD "${TMP_GZ_BACKUP_FILE}"
GUNZIP_EX_CODE=$?

if [[ "${GUNZIP_EX_CODE}" -ne $EX_OK ]]; then
	echo "gunzip exited with error code: ${GUNZIP_EX_CODE}"
	exit $GUNZIP_EX_CODE
fi

$PSQL_CMD -d "postgresql://${ARG_DB_USERNAME}:${ARG_DB_PASSWORD}@${ARG_DB_HOSTNAME}/${ARG_DB_CATALOG}" &>/dev/null <<EOF
drop schema public cascade;
create schema public;
drop extension pgcrypto;
drop schema crypto cascade;
drop schema new_schema cascade;
delete from pg_catalog.pg_largeobject where 1=1;
delete from pg_catalog.pg_largeobject_metadata where 1=1;
EOF
PSQL_EX_CODE=$?

if [[ "${PSQL_EX_CODE}" -ne $EX_OK ]]; then
	echo "psql exited with error code: ${PSQL_EX_CODE}"
	exit $PSQL_EX_CODE
fi

$PG_RESTORE_CMD -xO1 -F t -d "postgresql://${ARG_DB_USERNAME}:${ARG_DB_PASSWORD}@${ARG_DB_HOSTNAME}/${ARG_DB_CATALOG}" "${TMP_BACKUP_FILE}"
PG_RESTORE_EX_CODE=$?
if [[ "${PG_RESTORE_EX_CODE}" -ne $EX_OK ]]; then
	echo "pg_restore exited with error code: ${PG_RESTORE_EX_CODE}"
	exit $PG_RESTORE_EX_CODE
fi

$RM_CMD -rf "${TMP_FOLDER}"
