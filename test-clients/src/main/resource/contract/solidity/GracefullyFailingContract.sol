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

    function performInvalidlyFormattedFunctionCall(address sender, address[] memory tokens) public {
        precompileAddress.delegatecall(abi.encodeWithSelector(FakeHederaTokenService.associateTokens.selector, sender));

        int firstSuccessResponse = HederaTokenService.associateTokens(sender, tokens);

        if (firstSuccessResponse != HederaResponseCodes.SUCCESS) {
            revert ("Multiple associations Failed");
        }

        int secondSuccessResponse = HederaTokenService.dissociateTokens(sender, tokens);

        if (secondSuccessResponse != HederaResponseCodes.SUCCESS) {
            revert ("Multiple Dissociations Failed");
        }
    }
}

interface FakeHederaTokenService {
    function fakeFunction(address account) external;
    function associateTokens(address account) external returns (int responseCode);
}



