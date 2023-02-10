#! /bin/sh
if [ $# -lt 1 ]; then
  echo "USAGE: $0 host [default_node=3]"
  exit 1
fi
HOST=$1
DEFAULT_NODE=${2:-3}

source run/functions.sh

run/umbrella.sh $HOST $DEFAULT_NODE PreFreeze
mvnExec regression.umbrella.FreezeServiceTest

# read -p "Have inventory services been restarted on 18.206.130.28? " YN
# if [ ! $YN = "yes" ]; then
#   exit 1
# fi
# read -p "Was state restoration verified on an inventory node? " YN
# if [ ! $YN = "yes" ]; then
#   exit 1
# fi
# run/umbrella.sh $HOST $DEFAULT_NODE PostFreeze

mvnExec CI.MultipleCryptoTransfers $HOST
mvnExec crypto.MultiSigCreationTransfer $HOST
mvnExec regression.SmartContractBitcarbon 1
mvnExec regression.SmartContractCallLocal 1
mvnExec regression.SmartContractCreateContract 1
mvnExec regression.SmartContractCRUD 1
mvnExec regression.SmartContractDeletePayable 1
mvnExec regression.SmartContractInlineAssembly 1
mvnExec regression.SmartContractNegativeCalls 1
mvnExec regression.SmartContractNegativeCreates 1
mvnExec regression.SmartContractPay 1
mvnExec regression.SmartContractPayReceivable 1
mvnExec regression.SmartContractPayReceivableAmount 1
mvnExec regression.SmartContractSimpleStorage 1
mvnExec regressionSmartContractSimpleStorageWithEvents 1
mvnExec smartcontract.OCTokenIT
mvnExec smartcontract.BigArray $HOST $DEFAULT_NODE 1 32
mvnExec crypto.MultiSigCreationTransfer $HOST
mvnExec CI.SmartContractFailFirst
mvnExec SmartContractSelfDestruct
# mvnExec CI.SmartContractFeeTests
