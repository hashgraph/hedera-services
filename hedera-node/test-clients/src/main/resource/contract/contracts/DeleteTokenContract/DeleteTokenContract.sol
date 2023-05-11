// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.12;
import "./HederaTokenService.sol";
import "./HederaResponseCodes.sol";

contract DeleteTokenContract is HederaTokenService {

    function tokenDelete(address token)external{
        int response = HederaTokenService.deleteToken(token);

        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Token deletion failed!");
        }
    }
}