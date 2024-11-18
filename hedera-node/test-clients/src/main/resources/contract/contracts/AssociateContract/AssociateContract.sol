// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

import "./HederaTokenService.sol";
import "./HederaResponseCodes.sol";

contract AssociateContract is HederaTokenService{

    function associateToken(address token) external {
        int contractAssociated = HederaTokenService.associateToken(
            address(this), token);
        if (contractAssociated != HederaResponseCodes.SUCCESS) {
            revert("Failed to associate");
        }
    }

    function associateTokens(address[] memory tokens) external {
        int contractAssociated = HederaTokenService.associateTokens(
            address(this), tokens);
        if (contractAssociated != HederaResponseCodes.SUCCESS) {
            revert("Failed to associate");
        }
    }
}