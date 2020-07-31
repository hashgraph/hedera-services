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
#	$1 - Database server hostname/IP
# 	$2 - Database server port
#	$3 - Database server username
# 	$4 - Database server password
#	$5 - Database server catalog
#	$6 - Snapshot identifier
#	$7 - Swirlds Application ID
#	$8 - Swirlds World ID
#	$9 - Saved State Node Number
#	$10 - Saved State Round Number


ARG_DB_HOSTNAME="$1"
ARG_DB_PORT="$2"
ARG_DB_USERNAME="$3"
ARG_DB_PASSWORD="$4"
ARG_DB_CATALOG="$5"
ARG_SNAPSHOT_ID="$6"
ARG_SS_APP="$7"
ARG_SS_WORLD="$8"
ARG_SS_NODE="$9"
ARG_SS_ROUND="${10}"
ARG_SS_PATH="${11}"

#### Usage ####

usage() {
	echo "Usage: ${SCRIPT_NAME} <db_host_name> <db_port> <db_user> <db_password> <db_catalog> <snapshot_id> <application> <world_id> <node_number> <round_number> (optional: <saved_dir_path>)"
	exit $EX_USAGE
};

if [[ "$#" -lt 10 || "$#" -gt 11 ]]; then
	usage
fi

if [[ -z "${ARG_DB_HOSTNAME}" || -z "${ARG_DB_PORT}" ||  -z "${ARG_DB_USERNAME}" ||  -z "${ARG_DB_PASSWORD}" || -z "${ARG_DB_CATALOG}" ]]; then
	usage
fi

if [[ -z "${ARG_SNAPSHOT_ID}" || -z "${ARG_SS_APP}" || -z "${ARG_SS_WORLD}" || -z "${ARG_SS_NODE}" || -z "${ARG_SS_ROUND}" ]]; then
	usage
fi

if [[ -n "${ARG_SS_PATH}" ]]; then
  SAVED_STATE_PATH="${ARG_SS_PATH}"
else
  SAVED_STATE_PATH="$( cd "${SCRIPT_PATH}/../saved" && pwd )"
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

if [[ ${CFG_STORE_WITH_STATE} -eq 1 ]]; then
	CFG_STORAGE_PATH="$( cd "${SAVED_STATE_PATH}/${ARG_SS_APP}/${ARG_SS_NODE}/${ARG_SS_WORLD}/${ARG_SS_ROUND}" && pwd )"
fi

BACKUP_FILE="${CFG_STORAGE_PATH}/${CFG_FILE_NAME}"
GZ_BACKUP_FILE="${BACKUP_FILE}.gz"

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
