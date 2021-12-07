// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.6.12;

import "./HederaTokenService.sol";

contract AssociateDissociateContract is HederaTokenService {

    function associateToken(address tokenAddress) external {
        HederaTokenService.associateToken(msg.sender, tokenAddress);
    }

    function dissociateToken(address tokenAddress) external {
        HederaTokenService.dissociateToken(msg.sender, tokenAddress);
    }
}