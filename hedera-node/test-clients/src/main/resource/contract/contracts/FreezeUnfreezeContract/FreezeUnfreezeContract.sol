// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.12;
import "./HederaTokenService.sol";
import "./HederaResponseCodes.sol";

contract FreezeUnfreezeContract is HederaTokenService {

    function isTokenFrozen(address token, address account)external returns(bool){
        (int response,bool frozen) = HederaTokenService.isFrozen(token, account);

        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Token isFrozen failed!");
        }
        return frozen;
    }

    function tokenFreeze(address token, address account)external{
        int response = HederaTokenService.freezeToken(token, account);

        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Token freeze failed!");
        }
    }

    function tokenUnfreeze(address token, address account)external{
        int response = HederaTokenService.unfreezeToken(token, account);

        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Token unfreeze failed!");
        }
    }
}