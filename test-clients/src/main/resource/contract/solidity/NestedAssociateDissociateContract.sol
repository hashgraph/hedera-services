// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.6.12;

import "./HederaTokenService.sol";

contract NestedAssociateDissociateContract is HederaTokenService {

    AssociateDissociateContract associateDissociateContract;

    constructor(address associateDissociateContractAddress) public {
        associateDissociateContract = AssociateDissociateContract(associateDissociateContractAddress);
    }

    function associateDissociateContractCall(address sender, address tokenAddress) external {
        associateDissociateContract.tokenAssociate(sender, tokenAddress);
        int response = HederaTokenService.dissociateToken(sender, tokenAddress);
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Dissociate Failed");
        }
    }

    function dissociateAssociateContractCall(address sender, address tokenAddress) external {
        int response = HederaTokenService.dissociateToken(sender, tokenAddress);
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Dissociate Failed");
        }
        associateDissociateContract.tokenAssociate(sender, tokenAddress);
    }
}

contract AssociateDissociateContract is HederaTokenService {

    function tokenAssociate(address sender, address tokenAddress) external {
        HederaTokenService.associateToken(sender, tokenAddress);
    }

    function tokenDissociate(address sender, address tokenAddress) external {
        HederaTokenService.dissociateToken(sender, tokenAddress);
    }
}
