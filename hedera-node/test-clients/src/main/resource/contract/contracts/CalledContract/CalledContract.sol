// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.12;

import "./HederaTokenService.sol";
import "./HederaResponseCodes.sol";

contract CalledContract is HederaTokenService {
    function associate(address account, address tokenAddress) external {
        int response2 = HederaTokenService.associateToken(account, tokenAddress);

        if (response2 != HederaResponseCodes.SUCCESS) {
            revert ("Token associate failed");
        }
    }
}