// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.6.12;
import "./HederaTokenService.sol";
import "./HederaResponseCodes.sol";

contract NestedBurn is HederaTokenService {

    BurnTokenContract burnTokenContract;

    constructor(address burnTokenContractAddress) public {
        burnTokenContract = BurnTokenContract(burnTokenContractAddress);
    }

   function BurnAfterNestedMint(uint64 amount, address tokenAddress, int64[] memory serialNumbers) public {
       burnTokenContract.mintToken(amount, tokenAddress);

        int response = HederaTokenService.burnToken(tokenAddress, amount, serialNumbers);

        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Token burn failed");
        }

   }

}

contract BurnTokenContract is HederaTokenService {

   function mintToken(uint64 amount, address tokenAddress) public {
       int response = HederaTokenService.mintToken(tokenAddress, amount, new bytes(0));

        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Token mint failed");
        }
   }

}