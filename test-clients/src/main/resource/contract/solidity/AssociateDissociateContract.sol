// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.6.12;

import "./HederaTokenService.sol";

contract AssociateDissociateContract is HederaTokenService {

    function tokenAssociate(address sender, address tokenAddress) external {
        HederaTokenService.associateToken(sender, tokenAddress);
    }

    function tokenDissociate(address sender, address tokenAddress) external {
        HederaTokenService.dissociateToken(sender, tokenAddress);
    }
}