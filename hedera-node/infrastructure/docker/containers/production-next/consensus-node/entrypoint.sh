#!/usr/bin/env bash

########################################################################################################################
# Copyright 2016-2022 Hedera Hashgraph, LLC                                                                            #
#                                                                                                                      #
# Licensed under the Apache License, Version 2.0 (the "License");                                                      #
# you may not use this file except in compliance with the License.                                                     #
# You may obtain a copy of the License at                                                                              #
#                                                                                                                      #
#     http://www.apache.org/licenses/LICENSE-2.0                                                                       #
#                                                                                                                      #
# Unless required by applicable law or agreed to in writing, software                                                  #
# distributed under the License is distributed on an "AS IS" BASIS,                                                    #
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.                                             #
# See the License for the specific language governing permissions and                                                  #
# limitations under the License.                                                                                       #
########################################################################################################################

set -eo pipefail

SCRIPT_PATH="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
cd "${SCRIPT_PATH}" || exit 64

if [[ -z "${JAVA_OPTS}" ]]; then
  JAVA_OPTS=""
fi

# Setup Heap Options
JAVA_HEAP_OPTS=""

if [[ -n "${JAVA_HEAP_MIN}" ]]; then
  JAVA_HEAP_OPTS="${JAVA_HEAP_OPTS} -Xms${JAVA_HEAP_MIN}"
fi

if [[ -n "${JAVA_HEAP_MAX}" ]]; then
  JAVA_HEAP_OPTS="${JAVA_HEAP_OPTS} -Xmx${JAVA_HEAP_MAX}"
fi

# Setup Main Class
[[ -z "${JAVA_MAIN_CLASS}" ]] && JAVA_MAIN_CLASS="com.swirlds.platform.Browser"

# Setup Classpath
JCP_OVERRIDDEN="false"
if [[ -z "${JAVA_CLASS_PATH}" ]]; then
  JAVA_CLASS_PATH="data/lib/*"
else
  JCP_OVERRIDDEN="true"
fi

if [[ "${JCP_OVERRIDDEN}" != true && "${JAVA_MAIN_CLASS}" != "com.swirlds.platform.Browser" ]]; then
  JAVA_CLASS_PATH="${JAVA_CLASS_PATH}:data/apps/*"
fi

# Override Log Directory Name (if provided)
LOG_DIR_NAME="${LOG_DIR_NAME:-output}"

# Ensure the log directory exists
if [[ ! -d "${SCRIPT_PATH}/${LOG_DIR_NAME}" ]]; then
 mkdir -p "${SCRIPT_PATH}/${LOG_DIR_NAME}"
fi

cat <<EOF


****************        ****************************************************************************************
************                ************                                                                       *
*********                      *********                                                                       *
******                            ******                                                                       *
****                                ****      ___           ___           ___           ___           ___      *
***        ĦĦĦĦ          ĦĦĦĦ        ***     /\  \         /\  \         /\  \         /\  \         /\  \     *
**         ĦĦĦĦ          ĦĦĦĦ         **    /::\  \       /::\  \       /::\  \       /::\  \       /::\  \    *
*          ĦĦĦĦĦĦĦĦĦĦĦĦĦĦĦĦĦĦ          *   /:/\:\  \     /:/\:\  \     /:/\:\  \     /:/\:\  \     /:/\:\  \   *
           ĦĦĦĦĦĦĦĦĦĦĦĦĦĦĦĦĦĦ             /::\~\:\  \   /:/  \:\__\   /::\~\:\  \   /::\~\:\  \   /::\~\:\  \  *
           ĦĦĦĦ          ĦĦĦĦ            /:/\:\ \:\__\ /:/__/ \:|__| /:/\:\ \:\__\ /:/\:\ \:\__\ /:/\:\ \:\__\ *
           ĦĦĦĦĦĦĦĦĦĦĦĦĦĦĦĦĦĦ            \:\~\:\ \/__/ \:\  \ /:/  / \:\~\:\ \/__/ \/_|::\/:/  / \/__\:\/:/  / *
*          ĦĦĦĦĦĦĦĦĦĦĦĦĦĦĦĦĦĦ          *  \:\ \:\__\    \:\  /:/  /   \:\ \:\__\      |:|::/  /       \::/  /  *
**         ĦĦĦĦ          ĦĦĦĦ         **   \:\ \/__/     \:\/:/  /     \:\ \/__/      |:|\/__/        /:/  /   *
***        ĦĦĦĦ          ĦĦĦĦ        ***    \:\__\        \::/__/       \:\__\        |:|  |         /:/  /    *
****                                ****     \/__/         ~~            \/__/         \|__|         \/__/     *
******                            ******                                                                       *
*********                      *********                                                                       *
************                ************                                                                       *
****************        ****************************************************************************************


#### Starting Hedera Consensus Node Software ####


EOF

echo ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> BEGIN USER IDENT >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>"
id
echo "<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<< END USER IDENT   <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<"
echo

echo ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> BEGIN JAVA VERSION >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>"
/usr/bin/env java -version
echo "<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<< END JAVA VERSION   <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<"
echo

set +e
echo ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> BEGIN NODE OUTPUT >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>"
/usr/bin/env java ${JAVA_HEAP_OPTS} ${JAVA_OPTS} -cp "${JAVA_CLASS_PATH}" "${JAVA_MAIN_CLASS}"
EC="${?}"
echo "<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<< END NODE OUTPUT   <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<"

echo
echo ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> BEGIN EXIT CODE >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>"
printf "Process Exit Code: %s\n" "${EC}"
echo "<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<< END EXIT CODE   <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<"

printf "\n\n#### Hedera Services Node Software Stopped ####\n\n"
