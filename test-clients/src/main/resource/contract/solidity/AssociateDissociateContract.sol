// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.6.12;

import "./HederaTokenService.sol";

contract AssociateDissociateContract is HederaTokenService {

    address tokenAddress;

    constructor(address _tokenAddress) public {
        tokenAddress = _tokenAddress;
    }

    function associateToken() external {
        HederaTokenService.associateToken(msg.sender, tokenAddress);
    }

    function dissociateToken() external {
        HederaTokenService.dissociateToken(msg.sender, tokenAddress);
    }
}