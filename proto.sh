#!/bin/bash
export ECHO=''
export repositoryLocation="${HOME}/Code/hedera-services-new"
export hapiSubfolder='hedera-node/hapi'
export hapiBuildOutputs=(build' checkouts.bin' 'hedera-protobufs/')
export settingsFile='settings.gradle.kts'
export backupExtension='.bark_backup'
export backupFileName="${settingsFile}${backupExtension}"
if [[ $# -lt 1 ]]
then
  echo 'Please specify the branch in hedera-protobufs to verify.'
  echo
  echo "e.g.  $0 "'"add-pbj-types-for-state"'
  echo
else
  export branchToVerify="${1}"
  pushd "${repositoryLocation}"
  pushd "${hapiSubfolder}"
  (${ECHO} rm -rf ${hapiBuildOutputs[*]})
  popd
  if [[ ! -f ${backupFileName} ]]
  then
    ${ECHO} sed -i "${backupExtension}" -e 's/val hapiProtoBranchOrTag = "\(.*\)"/val hapiProtoBranchOrTag = "'"${branchToVerify}"'"/' "${settingsFile}"
    (${ECHO} ./gradlew clean :hapi:assemble --no-build-cache --no-configuration-cache) || (echo;echo;echo `FAILED`;echo;echo)
    if [[ -f ${backupFileName} ]]
    then
      ${ECHO} rm "${settingsFile}"
      ${ECHO} mv "${backupFileName}" "${settingsFile}"
    fi
  else
    echo Unable to proceed, leftover backup file "'"${backupFileName}"'" must be removed first.
  fi
  popd
fi
