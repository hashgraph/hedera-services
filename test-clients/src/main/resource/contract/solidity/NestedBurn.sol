// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.6.12;
import "./HederaTokenService.sol";
import "./HederaResponseCodes.sol";

contract NestedBurn is HederaTokenService {

    MintTokenContract mintTokenContract;

    constructor(address mintTokenContractAddress) public {
        mintTokenContract = MintTokenContract(mintTokenContractAddress);
    }

   function BurnAfterNestedMint(uint64 amount, address tokenAddress, int64[] memory serialNumbers) public {
       mintTokenContract.mintToken(amount, tokenAddress);

        int response = HederaTokenService.burnToken(tokenAddress, amount, serialNumbers);

        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Token burn failed");
        }

   }

}

contract MintTokenContract is HederaTokenService {

   function mintToken(uint64 amount, address tokenAddress) public {
       int response = HederaTokenService.mintToken(tokenAddress, amount, new bytes(0));

        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Token mint failed");
        }
   }

}