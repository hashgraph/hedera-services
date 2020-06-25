#!/usr/bin/env bash

. ${REPO}/.circleci/scripts/rationalize-ansible-env-vars.sh


PLAYBOOK_OPT="-i ./inventory/hosts-${TF_WORKSPACE}.yml \
      -u ubuntu \
      -e branch=${CIRCLE_BRANCH} \
      -e app_dir=${REPO} \
      -e enable_newrelic=${ENABLE_NEW_RELIC} \
      -e new_relic_name=${NEW_RELIC_NAME}"

function ansible_hugepage {
  cd $ANSIBLE_DIR
  cat play-hugepage-psql.yml
  ansible-playbook ${PLAYBOOK_OPT} play-hugepage-psql.yml
  cat play-deploy-psql.yml
  ansible-playbook  ${PLAYBOOK_OPT} \
      -e hgcapp_service_file=m410perf \
      play-deploy-psql.yml
}

function ansible_deploy {
  cd $ANSIBLE_DIR
  cat play-deploy-psql.yml
  ansible-playbook ${PLAYBOOK_OPT}  play-deploy-psql.yml
}

function ansible_clean {
  cd $TF_DIR
  if [[ -z $USE_EXISTING_NETWORK ]]; then
    TF_WORKSPACE=$(ls -1 $TF_DIR/nets | grep test)
  fi

  echo "Now TF_WORKSPACE: $TF_WORKSPACE"

  cd $ANSIBLE_DIR
  ansible-playbook \
      -i ./inventory/hosts-${TF_WORKSPACE}.yml \
      -u ubuntu \
      play-clean-state.yml
}

function ansible_reboot {
  cd $TF_DIR
  if [[ -z $USE_EXISTING_NETWORK ]]; then
    TF_WORKSPACE=$(ls -1 $TF_DIR/nets | grep test)
  fi

  echo "Now TF_WORKSPACE: $TF_WORKSPACE"

  cd $ANSIBLE_DIR
  ansible-playbook \
      -i ./inventory/hosts-${TF_WORKSPACE}.yml \
      -u ubuntu \
      play-reboot.yml
}

function ansible_prepare {
  cd $ANSIBLE_DIR
  ansible-playbook \
      -i ./inventory/hosts-${TF_WORKSPACE}.yml \
      -u ubuntu \
      play-uninstall-psql.yml
}
