// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.12;
pragma experimental ABIEncoderV2;

import "./HederaTokenService.sol";
import "./HederaResponseCodes.sol";
import "./MintToken.sol";

contract NestedBurn is HederaTokenService {

    MintTokenContract mintTokenContract;

    constructor(address _mintTokenContractAddress) public {
        mintTokenContract = MintTokenContract(_mintTokenContractAddress);
    }

   function burnAfterNestedMint(int64 amount, address tokenAddress, int64[] memory serialNumbers) public {
       mintTokenContract.mintToken(amount, tokenAddress);

        (int response, int64 newTotalSupply) = HederaTokenService.burnToken(tokenAddress, amount, serialNumbers);

        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Token burn failed");
        }
   }
}