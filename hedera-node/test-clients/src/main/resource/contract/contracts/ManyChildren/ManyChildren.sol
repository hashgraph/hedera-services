// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.6.0 <0.9.0;
pragma experimental ABIEncoderV2;

import "./hip-206/HederaTokenService.sol";
import "./IERC20.sol";

contract ManyChildren is HederaTokenService {

    function transferTokensCall(address token, address sender, address receiver, int64 amount) public {
        HederaTokenService.transferToken(token, sender, receiver, amount);
    }

    function createThingsRepeatedly(
        uint thingsToCreate,
        address token,
        address sender,
        address receiver,
        int64 amount
    ) external {
        for (uint i = 0; i < thingsToCreate; i++) {
        transferTokensCall.token(token, sender, receiver, amount);
        }    
    }

}
