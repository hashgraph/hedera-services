// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.6.12;

import "./HederaTokenService.sol";

contract GracefullyFailingContract is HederaTokenService {

    function performNonExistingServiceFunctionCall(address sender, address tokenAddress) public {
        precompileAddress.delegatecall(abi.encodeWithSelector(FakeHederaTokenService.fakeFunction.selector, address(this)));

        int firstSuccessResponse = HederaTokenService.associateToken(sender, tokenAddress);

        if (firstSuccessResponse != HederaResponseCodes.SUCCESS) {
            revert ("Associate Failed");
        }

        int secondSuccessResponse = HederaTokenService.dissociateToken(sender, tokenAddress);

        if (secondSuccessResponse != HederaResponseCodes.SUCCESS) {
            revert ("Dissociate Failed");
        }
    }
}

interface FakeHederaTokenService {
    function fakeFunction(address account) external;
}

