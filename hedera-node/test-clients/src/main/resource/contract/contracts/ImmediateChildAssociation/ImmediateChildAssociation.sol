// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.6.0 <0.9.0;

import "./HederaTokenService.sol";
import "./HederaResponseCodes.sol";

contract ImmediateChildAssociation is HederaTokenService {
    constructor(address tokenAddr) {
        AssociationTarget child = new AssociationTarget();
        // If we were created with a cryptographic adminKey, two things are true:
        //   (1) It will have signed the ContractCreate HAPI transaction; and,
        //   (2) Our child will inherit that adminKey
        // So the below should succeed:
        address childAddr = address(child);
        int rc = HederaTokenService.associateToken(childAddr, tokenAddr);
        if (rc != HederaResponseCodes.SUCCESS) {
            revert("Could not associate account");
        }
    }
}

contract AssociationTarget {
}
