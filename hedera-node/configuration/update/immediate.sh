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
# xr_insecure_stage_upgrade: Handles any required preparation and environment staging prior to the freeze window.
#
#   * This method MAY NOT take any actions that would potentially interfere with the running containers, files needed
#       by the running containers, modify the backing data stores (PostgreSQL, etc), or perform any host system
#       modifications that alter the running/active environment.
#
#   * This method MAY take backups of the current files used by the running containers so long as those backups do not
#        impede, inhibit, block, or otherwise interfere with the running production containers.
#
#   * This method MAY build new docker container images and may stage files in a temporary or working directory.
#
#   * This method MAY prepare host level changes so long as they are not applied or become effective until the
#       freeze window begins.
################################################
function xr_insecure_stage_upgrade {
  local upgrade_source_path="${1}"
  local host_root_install_path="${2}"
  local host_application_root_path="${3}"
  local current_application_version="${4}"
  local new_application_version="${5}"

  return "${EX_OK}"
}

################################################
# xr_insecure_rollback_staged_upgrade: Handles any required rollback of ALL actions taken during the
#                                         `xr_insecure_stage_upgrade` method.
#
#   * This method MUST take appropriate actions to cleanup any files staged by the `xr_insecure_stage_upgrade` method.
#
#   * This method MUST revert any prepared (but not applied) host system changes made by the
#       `xr_insecure_stage_upgrade` method.
#
#   * This method MUST remove any docker container images that were created during the
#       `xr_insecure_stage_upgrade` method.
#
#   * This method MUST NOT make any additional changes outside of actions to revert the changes made by the
#       `xr_insecure_stage_upgrade` method.
################################################
function xr_insecure_rollback_staged_upgrade {
  local upgrade_source_path="${1}"
  local host_root_install_path="${2}"
  local host_application_root_path="${3}"
  local current_application_version="${4}"
  local new_application_version="${5}"

  return "${EX_OK}"
}
