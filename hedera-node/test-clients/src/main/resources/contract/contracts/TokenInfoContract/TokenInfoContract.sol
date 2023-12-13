// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;
pragma experimental ABIEncoderV2;

import "./FeeHelper.sol";

contract TokenInfoContract is FeeHelper {

    function getInformationForToken(address token) external returns (IHederaTokenService.TokenInfo memory tokenInfo) {
        (int responseCode, IHederaTokenService.TokenInfo memory retrievedTokenInfo) = HederaTokenService.getTokenInfo(token);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }

        tokenInfo = retrievedTokenInfo;
    }

    function updateInformationForTokenAndGetLatestInformation(address tokenId, string calldata name,
        string calldata symbol, address treasury, string calldata memo) external returns (IHederaTokenService.TokenInfo memory tokenInfo) {
        IHederaTokenService.HederaToken memory token;
        token.name = name;
        token.symbol = symbol;
        token.treasury = treasury;
        token.memo = memo;

        HederaTokenService.updateTokenInfo(tokenId, token);

        (int responseCode, IHederaTokenService.TokenInfo memory retrievedTokenInfo) = HederaTokenService.getTokenInfo(tokenId);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }

        tokenInfo = retrievedTokenInfo;
    }

    function getInformationForFungibleToken(address token) external returns (IHederaTokenService.FungibleTokenInfo memory fungibleTokenInfo) {
        (int responseCode, IHederaTokenService.FungibleTokenInfo memory retrievedTokenInfo) = HederaTokenService.getFungibleTokenInfo(token);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }

        fungibleTokenInfo = retrievedTokenInfo;
    }

    function updateInformationForFungibleTokenAndGetLatestInformation(address tokenId, string calldata name,
        string calldata symbol, address treasury, string calldata memo) external returns (IHederaTokenService.FungibleTokenInfo memory fungibleTokenInfo) {
        IHederaTokenService.HederaToken memory token;
        token.name = name;
        token.symbol = symbol;
        token.treasury = treasury;
        token.memo = memo;

        HederaTokenService.updateTokenInfo(tokenId, token);

        (int responseCode, IHederaTokenService.FungibleTokenInfo memory retrievedTokenInfo) = HederaTokenService.getFungibleTokenInfo(tokenId);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }

        fungibleTokenInfo = retrievedTokenInfo;
    }


    function getInformationForNonFungibleToken(address token, int64 serialNumber) external returns (IHederaTokenService.NonFungibleTokenInfo memory nonFungibleTokenInfo) {
        (int responseCode, IHederaTokenService.NonFungibleTokenInfo memory retrievedTokenInfo) = HederaTokenService.getNonFungibleTokenInfo(token, serialNumber);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }

        nonFungibleTokenInfo = retrievedTokenInfo;
    }

    function updateInformationForNonFungibleTokenAndGetLatestInformation(address tokenId, int64 serialNumber, string calldata name,
        string calldata symbol, address treasury, string calldata memo) external returns (IHederaTokenService.NonFungibleTokenInfo memory nonFungibleTokenInfo) {
        IHederaTokenService.HederaToken memory token;
        token.name = name;
        token.symbol = symbol;
        token.treasury = treasury;
        token.memo = memo;

        HederaTokenService.updateTokenInfo(tokenId, token);

        (int responseCode, IHederaTokenService.NonFungibleTokenInfo memory retrievedTokenInfo) = HederaTokenService.getNonFungibleTokenInfo(tokenId, serialNumber);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }

        nonFungibleTokenInfo = retrievedTokenInfo;
    }

    function updateTokenKeysAndReadLatestInformation(
        address token,
        address contractID) public payable {

        IHederaTokenService.TokenKey[] memory keys = new IHederaTokenService.TokenKey[](7);
        keys[0] = getSingleKey(0, 2, contractID);
        keys[1] = getSingleKey(1, 2, contractID);
        keys[2] = getSingleKey(2, 2, contractID);
        keys[3] = getSingleKey(3, 2, contractID);
        keys[4] = getSingleKey(4, 2, contractID);
        keys[5] = getSingleKey(6, 2, contractID);
        keys[6] = getSingleKey(5, 2, contractID);

        int responseCode = super.updateTokenKeys(token, keys);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ("Update of token keys failed!");
        }

        HederaTokenService.getTokenKey(token, 1);
        HederaTokenService.getTokenKey(token, 2);
        HederaTokenService.getTokenKey(token, 4);
        HederaTokenService.getTokenKey(token, 8);
        HederaTokenService.getTokenKey(token, 16);
        HederaTokenService.getTokenKey(token, 32);
        HederaTokenService.getTokenKey(token, 64);
    }

    function getCustomFeesForToken(address token) external returns (
        IHederaTokenService.FixedFee[] memory fixedFees,
        IHederaTokenService.FractionalFee[] memory fractionalFees,
        IHederaTokenService.RoyaltyFee[] memory royaltyFees) {
        int responseCode;
        (responseCode, fixedFees, fractionalFees, royaltyFees) = HederaTokenService.getTokenCustomFees(token);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }
    }
}
