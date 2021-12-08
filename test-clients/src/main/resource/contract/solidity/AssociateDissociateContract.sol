// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.6.12;

import "./HederaTokenService.sol";

contract AssociateDissociateContract is HederaTokenService {

    function tokenAssociate(address sender, address tokenAddress) external {
        int response = HederaTokenService.associateToken(sender, tokenAddress);

        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Associate Failed");
        }
    }

    function tokenDissociate(address sender, address tokenAddress) external {
        int response = HederaTokenService.dissociateToken(sender, tokenAddress);

        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Dissociate Failed");
        }
    }
}