// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.6.12;
pragma experimental ABIEncoderV2;

import "./hip-206/HederaTokenService.sol";
import "./hip-206/HederaResponseCodes.sol";

contract NestedBurn is HederaTokenService {

    MintTokenContract mintTokenContract;

    constructor(address _mintTokenContractAddress) public {
        mintTokenContract = MintTokenContract(_mintTokenContractAddress);
    }

   function burnAfterNestedMint(uint64 amount, address tokenAddress, int64[] memory serialNumbers) public {
       mintTokenContract.mintToken(amount, tokenAddress);

        (int response, uint64 newTotalSupply) = HederaTokenService.burnToken(tokenAddress, amount, serialNumbers);

        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Token burn failed");
        }

   }

}

contract MintTokenContract is HederaTokenService {

   function mintToken(uint64 amount, address tokenAddress) public {
       (int response, uint64 newTotalSupply, int[] memory serialNumbers) = HederaTokenService.mintToken(tokenAddress, amount, new bytes[](0));

        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Token mint failed");
        }
   }
}