// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.6.0 <0.9.0;

import "./HederaTokenService.sol";
import "./HederaResponseCodes.sol";

contract SelfAssociating is HederaTokenService {
    constructor(address tokenAddr) {
        address thisAddr = address(this);
        int rc = HederaTokenService.associateToken(thisAddr, tokenAddr);
        if (rc != HederaResponseCodes.SUCCESS) {
            revert("Could not associate account");
        }
    }
}