// SPDX-License-Identifier: Apache-2.0
pragma solidity 0.8.16;

import "./hip-206/HederaTokenService.sol";

/*
* @dev Minimal contract that validates consensus times are still 
* assigned with the pattern (parentConsensusTime + nonce) even 
* if the record of an internal creation is discarded.
*/
contract ConsTimeRepro is HederaTokenService {
    function createChildThenFailToAssociate(address sender, address tokenAddress) external {
        Child child = new Child();

        int response = HederaTokenService.associateToken(sender, tokenAddress);

        require(response == HederaResponseCodes.SUCCESS);
    }
}

contract Child {
}
