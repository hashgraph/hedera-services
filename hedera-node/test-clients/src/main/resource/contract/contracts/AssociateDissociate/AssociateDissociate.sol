// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.12;

import "./HederaResponseCodes.sol";
import "./IHederaTokenService.sol";

contract AssociateDissociateContract {

    IHederaTokenService constant HederaTokenService = IHederaTokenService(address(0x167));

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

    function tokensAssociate(address account, address[] memory tokens) external {
        int response = HederaTokenService.associateTokens(account, tokens);

        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Multiple Associations Failed");
        }
    }

    function tokensDissociate(address account, address[] memory tokens) external {
        int response = HederaTokenService.dissociateTokens(account, tokens);

        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Multiple Dissociations Failed");
        }
    }
}
