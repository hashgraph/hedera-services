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

    function createThingsRepeatedly(uint thingsToCreate) external {
        for (uint i = 0; i < thingsToCreate; i++) {
            new Thing(i);
        }    
    }

    function sendSomeValueTo(address payable beneficiary) external payable {
        beneficiary.transfer(msg.value); 
    }
}

contract Thing {
    uint256 i;

    constructor(uint256 _i) {
        i = _i;
    }
}
