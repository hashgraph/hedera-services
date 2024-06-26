#! /bin/sh
if [ $# -lt 1 ]; then
  echo "USAGE: $0 [contract]"
  exit 1
fi

CONTRACT=${1}
mv ../bytecodes/${CONTRACT}.bin ../contracts/${CONTRACT}
cp ../bytecodes/${CONTRACT}_sol_${CONTRACT}.abi \
  ../contracts/${CONTRACT}/${CONTRACT}.json
rm ../bytecodes/${CONTRACT}_sol_*
rm ../bytecodes/IERC20_sol_IERC20.abi
rm ../bytecodes/IERC20_sol_IERC20.bin
rm ../bytecodes/hip-206_HederaResponseCodes_sol_HederaResponseCodes.abi
rm ../bytecodes/hip-206_HederaResponseCodes_sol_HederaResponseCodes.bin
rm ../bytecodes/hip-206_HederaTokenService_sol_HederaTokenService.abi
rm ../bytecodes/hip-206_HederaTokenService_sol_HederaTokenService.bin
rm ../bytecodes/hip-206_IHederaTokenService_sol_IHederaTokenService.abi
rm ../bytecodes/hip-206_IHederaTokenService_sol_IHederaTokenService.bin
