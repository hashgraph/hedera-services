#!/usr/bin/env bash

#### Script Info ####
SCRIPT_NAME="`basename "${BASH_SOURCE[0]}"`"
SCRIPT_PATH="$( cd "$(dirname "${BASH_SOURCE[0]}")" && pwd )"
SAVED_STATE_PATH="$( cd "${SCRIPT_PATH}/../saved" && pwd )"

#### Configuration ####

# Path where the backup should be stored - this can be local or any mounted NFS or other remote filesystem
# S3 or GCP Buckets can be mounted using s3fs-fuse or gcp-fuse drivers
CFG_STORAGE_PATH=""

# The file name of the tar file containing the backup
CFG_FILE_NAME="PostgresBackup.tar"

# If set to 1 then the CFG_STORAGE_PATH is ignored and is instead computed
CFG_STORE_WITH_STATE=1

# If set to 1 then the backup is written but then subsequently removed
CFG_REGRESSION_MODE=0


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

#### Usage ####

usage() {
	echo "Usage: ${SCRIPT_NAME} <db_host_name> <db_port> <db_user> <db_password> <db_catalog> <snapshot_id> <application> <world_id> <node_number> <round_number>"
	exit $EX_USAGE
};

if [[ "$#" -ne 10 ]]; then
	usage
fi

if [[ -z "${ARG_DB_HOSTNAME}" || -z "${ARG_DB_PORT}" ||  -z "${ARG_DB_USERNAME}" ||  -z "${ARG_DB_PASSWORD}" || -z "${ARG_DB_CATALOG}" ]]; then
	usage
fi

if [[ -z "${ARG_SNAPSHOT_ID}" || -z "${ARG_SS_APP}" || -z "${ARG_SS_WORLD}" || -z "${ARG_SS_NODE}" || -z "${ARG_SS_ROUND}" ]]; then
	usage
fi


#### Command Alias Definitions ####
# Uses /usr/bin/env to resolve the path to the programs

SUDO_CMD="/usr/bin/env sudo"
PG_DUMP_CMD="/usr/bin/env pg_dump"

CP_CMD="/usr/bin/env cp"
RSYNC_CMD="/usr/bin/env rsync"
GZIP_CMD="/usr/bin/env gzip"
RM_CMD="/usr/bin/env rm"



#### Program Execution

if [[ ${CFG_STORE_WITH_STATE} -eq 1 ]]; then
	CFG_STORAGE_PATH="$( cd "${SAVED_STATE_PATH}/${ARG_SS_APP}/${ARG_SS_NODE}/${ARG_SS_WORLD}/${ARG_SS_ROUND}" && pwd )"
fi

echo "Storage Path: ${CFG_STORAGE_PATH}"

BACKUP_FILE="${CFG_STORAGE_PATH}/${CFG_FILE_NAME}"
GZ_BACKUP_FILE="${BACKUP_FILE}.gz"

$PG_DUMP_CMD -F t -f "${BACKUP_FILE}" --snapshot="${ARG_SNAPSHOT_ID}" -d "postgresql://${ARG_DB_USERNAME}:${ARG_DB_PASSWORD}@${ARG_DB_HOSTNAME}/${ARG_DB_CATALOG}"
PG_DUMP_EX_CODE=$?

if [[ "${PG_DUMP_EX_CODE}" -ne $EX_OK ]]; then
	echo "Backup (pg_dump) exited with error code: ${PG_DUMP_EX_CODE}"
	exit $PG_DUMP_EX_CODE
fi

$GZIP_CMD "${BACKUP_FILE}"
GZIP_EX_CODE=$?

if [[ "${GZIP_EX_CODE}" -ne $EX_OK ]]; then
	echo "GZip exited with error code: ${GZIP_EX_CODE}"
	exit $GZIP_EX_CODE
fi


if [[ ${CFG_REGRESSION_MODE} -eq 1 ]]; then
	echo "Regression Mode"
	if [[ -f "${GZ_BACKUP_FILE}" ]]; then

		$RM_CMD "${GZ_BACKUP_FILE}"
	fi
fi