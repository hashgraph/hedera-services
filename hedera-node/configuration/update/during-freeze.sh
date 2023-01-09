#!/usr/bin/env bash

########################################################################################################################
# Copyright 2016-2023 Swirlds, Inc.                                                                                    #
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

################################################
#      !!!         IMPORTANT           !!!     #
################################################

# ALL code present in this file must be located in one of the function declarations below!
# There MUST NOT be ANY executable code in GLOBAL scope (ie: anything outside the function bodies).

# ALL function definitions listed below MUST be present and have the correct signature.
# The function names MUST NOT be changed in way!

# Any unused function MUST have at a minimum return an value of zero, which is represented by the
# constant EX_OK (eg: `return "${EX_OK}"`).

# ALL constant values and methods present in the Node Management Tools Commons module are available for use
# in the methods below.

# This file MUST use Unix line endings (eg: \n) and MUST NEVER use Windows line endings (eg: \r\n).

################################################
#      !!!         IMPORTANT           !!!     #
################################################


################################################
#      !!!        PHASE 1 NOTES        !!!     #
################################################

#   * The underlying framework will ensure that the containers are stopped and that a docker container image exists for
#       the currently deployed container versions prior to the methods below being invoked.
#
#   * The underlying framework will handle copying the `data/lib/*.jar`,`data/apps/*.jar`, `data/backup/*.sh` files
#       (if present in the upgrade package) into the docker container.
#
#   * The underlying framework will handle copying the `data/config/*`, `data/onboard/*`, `log4j2.xml`, `settings.txt`,
#       and `config.txt` files (if present in the upgrade package) to the appropriate locations on the host system.
#
#   * The underlying framework will handle building an new docker container image after the above items are copied AND
#       the appropriate method(s) below are successfully executed.
#
#   * Any other files, not listed above, that need to be copied from the upgrade package to the host system or into the
#       docker container MUST be handled in the appropriate method(s) below.

################################################
#      !!!        PHASE 1 NOTES        !!!     #
################################################


################################################
# uploader_mirror_apply_staged_update: applies previously staged update of compose .env file
################################################
function uploader_mirror_apply_staged_update {
  local -r uploader_mirror_compose_path="/opt/hgcapp/uploader-mirror"
  local -r uploader_mirror_staged_env_path="${uploader_mirror_compose_path}/record.env.staged"
  local -r uploader_mirror_active_env_path="${uploader_mirror_compose_path}/record.env"
  local -r retries=1
  local exit_code

  if [ -f $uploader_mirror_staged_env_path ]; then
    log_info "${LOG_MARKER_UPGRADE}" "Uploader Install: Detected staged .env update for uploader-mirror at ${uploader_mirror_staged_env_path}"

    cp $uploader_mirror_staged_env_path $uploader_mirror_active_env_path

    if [[ ! -d "${uploader_mirror_compose_path}" || ! -f "${uploader_mirror_compose_path}/${DKRC_DEFAULT_COMPOSE_FILE}" ]]; then
      log_error "${LOG_MARKER_SETUP}" "Definition: Compose Definition Not Found [ file = '${uploader_mirror_compose_path}/${DKRC_DEFAULT_COMPOSE_FILE}' ]"
      exit_code="${EX_OK}"
    fi

    dkrc_execute_std_command_ex "${uploader_mirror_compose_path}" up "" ${retries} 1 --detach "${@}"
    exit_code="${?}"

    rm $uploader_mirror_staged_env_path

  else
    log_info "${LOG_MARKER_UPGRADE}" "Uploader Install: No staged .env update for uploader-mirror detected; no changes to uploader-mirror deployment will occur"
    exit_code="${EX_OK}"
  fi

  return "${exit_code}"
}

################################################
# xr_insecure_perform_freeze_upgrade: TODO - Provide docs
################################################
function xr_insecure_perform_freeze_upgrade {
  local upgrade_source_path="${1}"
  local host_root_install_path="${2}"
  local host_application_root_path="${3}"
  local current_application_version="${4}"
  local new_application_version="${5}"

  uploader_mirror_apply_staged_update

  return "${EX_OK}"
}

################################################
# xr_insecure_rollback_freeze_upgrade: TODO - Provide docs
################################################
function xr_insecure_rollback_freeze_upgrade {
  local upgrade_source_path="${1}"
  local host_root_install_path="${2}"
  local host_application_root_path="${3}"
  local current_application_version="${4}"
  local new_application_version="${5}"

  return "${EX_OK}"
}
