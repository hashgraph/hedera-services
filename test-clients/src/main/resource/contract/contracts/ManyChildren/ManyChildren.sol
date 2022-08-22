// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.6.0 <0.9.0;
pragma experimental ABIEncoderV2;

import "./hip-206/HederaTokenService.sol";
import "./IERC20.sol";

contract ManyChildren is HederaTokenService {
    function checkBalanceRepeatedly(address token, address account, uint timesToCheck) external {
        for (uint i = 0; i < timesToCheck; i++) {
            IERC20(token).balanceOf(account);
        }    
    }
}
