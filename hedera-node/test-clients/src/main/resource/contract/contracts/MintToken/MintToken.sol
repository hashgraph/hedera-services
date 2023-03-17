// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.12;
pragma experimental ABIEncoderV2;

import "./HederaTokenService.sol";
import "./HederaResponseCodes.sol";

contract MintTokenContract is HederaTokenService {

    function mintToken(int64 amount, address tokenAddress) public {
        (int response, int64 newTotalSupply, int64[] memory serialNumbers) = HederaTokenService.mintToken(tokenAddress, amount, new bytes[](0));

        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Token mint failed");
        }
    }
}