// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.4.9 <0.9.0;

import "./HederaScheduleService.sol";
import "./HederaResponseCodes.sol";
import "./KeyHelper.sol";
pragma experimental ABIEncoderV2;

contract HIP756Contract is HederaScheduleService, KeyHelper {

    function scheduleCreateFT(address autoRenew, address treasury) external payable returns (int64 responseCode, address scheduleAddress) {
        IHederaTokenService.HederaToken memory token;
        token.treasury = treasury;
        IHederaTokenService.Expiry memory expiry;
        expiry.autoRenewAccount = autoRenew;
        token.expiry = expiry;

        token.name = "test";
        token.symbol = "TTT";

        (responseCode, scheduleAddress) = scheduleCreateFungibleToken(token, 1000, 10);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert("Failed to associate");
        }
    }


    function scheduleCreateNFT(address autoRenew, address treasury) external payable returns (int64 responseCode, address scheduleAddress) {
        IHederaTokenService.HederaToken memory token;
        token.name = "nft";
        token.symbol = "nft";
        token.treasury = address(treasury);
        IHederaTokenService.Expiry memory expiry;
        expiry.autoRenewAccount = autoRenew;
        token.expiry = expiry;
        token.tokenKeys = new IHederaTokenService.TokenKey[](1);
        IHederaTokenService.TokenKey memory tokenSupplyKey = KeyHelper.getSingleKey(KeyHelper.KeyType.SUPPLY, KeyHelper.KeyValueType.CONTRACT_ID, address(this));
        token.tokenKeys[0] = tokenSupplyKey;
        (responseCode, scheduleAddress) = scheduleCreateNonFungibleToken(token);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }
}