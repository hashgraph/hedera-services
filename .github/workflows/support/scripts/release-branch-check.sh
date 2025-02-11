#!/usr/bin/env bash
set -eo pipefail

# Access workflow environment
export WORKFLOW_CONFIG_FILE GITHUB_CLI_VERSION YQ_CLI_VERSION
export GITHUB_WORKSPACE GITHUB_REPOSITORY GITHUB_OUTPUT GITHUB_TOKEN GH_TOKEN

##### Prerequisites #####
if ! command -v gh >/dev/null 2>&1; then
  echo "::group::Installing Github CLI"
  curl -SsL "https://github.com/cli/cli/releases/download/v${GITHUB_CLI_VERSION}/gh_${GITHUB_CLI_VERSION}_linux_386.tar.gz" | \
    sudo tar -C /usr/local/bin -xzvf - --no-anchored --absolute-names --strip-components 2 gh

  GH="/usr/local/bin/gh"
  sudo chmod +x "${GH}"
  echo "::endgroup::"
else
  GH="$(command -v gh)"
fi

if ! command -v yq >/dev/null 2>&1; then
  echo "::group::Installing YQ CLI"

  YQ="/usr/local/bin/yq"
  sudo curl -SsL "https://github.com/mikefarah/yq/releases/download/v${YQ_CLI_VERSION}/yq_linux_386" -o "${YQ}"
  sudo chmod +x "${YQ}"
  echo "::endgroup::"
else
  YQ="$(command -v yq)"
fi

function fail {
    printf '%s\n' "$1" >&2  ## Send message to stderr. Exclude >&2 if you don't want it that way.
    exit "${2-1}"  ## Return a code specified by $2 or 1 by default.
}

echo "::group::Configuring Environment"
printf "Setting working directory.... "
cd "${GITHUB_WORKSPACE}" >/dev/null 2>&1 || fail "ERROR (Exit Code: ${?})" "${?}"
printf "DONE\n"

printf "Configuring execution context.... "
# Export installed or found program locations
export GH YQ
TODAY_START="$(date --date="00:00:00 today" '+%s')"
TODAY_END="$(date --date="23:59:59 today" '+%s')"

printf "DONE\n"
echo "::endgroup::"

##### Methods #####
function github_branch_exists {
  local branch_name="${1}"

  local located_name exit_code
  set +e
  located_name="$(${GH} api "repos/${GITHUB_REPOSITORY}/branches/${branch_name}" 2>/dev/null | ${YQ} -e '.name' 2>/dev/null | tr -d '\n')"
  exit_code="${?}"
  set -e

  [[ "${exit_code}" -ne 0 ]] && return 1
  [[ "${located_name}" != "${branch_name}" ]] && return 1

  return 0
}

function github_set_output {
  local key="${1}"
  local value="${2}"

  printf "%s=%s\n" "${key}" "${value}" >> "${GITHUB_OUTPUT}" || return "${?}"
  return 0
}

function config_list_size {
  local yaml_file="${1}"
  local query_path="${2}"
  local size=0

  [[ -f "${yaml_file}" ]] || return 1
  [[ -n "${query_path}" ]] || return 2

  size="$(${YQ} -e "${query_path} | length" "${yaml_file}" 2>/dev/null)"
  echo -n "${size}"
  return 0
}

function config_get_value {
  local yaml_file="${1}"
  local query_path="${2}"
  local value

  [[ -f "${yaml_file}" ]] || return 1
  [[ -n "${query_path}" ]] || return 2

  value="$(${YQ} -e "${query_path}" "${yaml_file}" 2>/dev/null)"
  echo -n "${value}"
  return 0
}

function date_occurs_today {
  local date="${1}"
  local date_epoch

  date_epoch="$(date --date="${date}" '+%s')"

  if [[ "${date_epoch}" -ge "${TODAY_START}" && "${date_epoch}" -le "${TODAY_END}" ]]; then
    return 0
  fi

  return 1
}

##### Main Entrypoint #####
function main {
  local i num_schedules trigger_time
  trigger_time="$(config_get_value "${WORKFLOW_CONFIG_FILE}" ".release.branching.execution.time")" || return "${?}"
  num_schedules="$(config_list_size "${WORKFLOW_CONFIG_FILE}" ".release.branching.schedule")" || return "${?}"

  for (( i=0; i < num_schedules; i++ )); do
    local on name tag_create tag_name
    on="$(config_get_value "${WORKFLOW_CONFIG_FILE}" ".release.branching.schedule[${i}].on")" || return "${?}"

    if date_occurs_today "${on} ${trigger_time}"; then
      name="$(config_get_value "${WORKFLOW_CONFIG_FILE}" ".release.branching.schedule[${i}].name")" || return "${?}"
      tag_create="$(config_get_value "${WORKFLOW_CONFIG_FILE}" ".release.branching.schedule[${i}].initial-tag.create")" || return "${?}"
      tag_name="$(config_get_value "${WORKFLOW_CONFIG_FILE}" ".release.branching.schedule[${i}].initial-tag.name")" || return "${?}"

      [[ -n "${tag_create}" ]] || tag_create="false"
      [[ -n "${tag_name}" ]] || tag_create="false"

      if [[ -z "${name}" ]]; then
        echo "Skipping the rule scheduled for '${on} ${trigger_time}' due to a blank or missing branch name...."
        continue
      fi

      if github_branch_exists "${name}"; then
        echo "Skipping the rule scheduled for '${on} ${trigger_time}' due to the '${name}' branch already existing...."
        continue
      fi

      github_set_output "schedule-trigger" "${on} ${trigger_time}"
      github_set_output "branch-create" "true" || return "${?}"
      github_set_output "branch-name" "${name}" || return "${?}"
      github_set_output "tag-create" "${tag_create}" || return "${?}"
      github_set_output "tag-name" "${tag_name}" || return "${?}"
    fi
  done

  return 0
}

echo "::group::Main Code Execution"
main
ec="${?}"
echo "Process Exit Code: ${ec}"
echo "::endgroup::"
exit "${ec}"
