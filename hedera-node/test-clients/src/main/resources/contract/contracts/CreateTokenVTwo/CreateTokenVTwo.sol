// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;
pragma experimental ABIEncoderV2;

import "./FeeHelper.sol";
import "./KeyHelper.sol";
import "./HederaResponseCodes.sol";
import "./HederaTokenService.sol";


contract CreateTokenVTwo is HederaTokenService, FeeHelper, KeyHelper {


    function createTokenWithMetadata() public payable returns(address createdAddress) {
        IHederaTokenService.HederaTokenV2 memory token;
        token.name = "testToken";
        token.metadata = bytes("asdasdasdads");
        token.symbol ="asd";
        token.treasury = address(this);
        token.tokenKeys = super.getDefaultKeys();

        (int responseCode, address tokenAddress) = HederaTokenService.createFungibleToken(token, 100, 4);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }

        createdAddress = tokenAddress;

    }
}