// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;
pragma experimental ABIEncoderV2;

import "./KeyHelper.sol";
import "./HederaResponseCodes.sol";
import "./HederaTokenService.sol";

contract CreateTokenVTwo is HederaTokenService, KeyHelper {
    function createTokenWithMetadata() public payable returns (address createdAddress) {
        IHederaTokenService.HederaTokenV2 memory token;
        token.name = "testToken";
        token.metadata = bytes("asdasdasdads");
        token.symbol = "asd";
        token.treasury = address(this);
        token.tokenKeys = super.getDefaultKeys();

        (int256 responseCode, address tokenAddress) = HederaTokenService.createFungibleToken(token, 100, 4);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }

        createdAddress = tokenAddress;
    }

    function updateTokenMetadata(address token, string memory metadata) public {
        IHederaTokenService.HederaTokenV2 memory tokenInfo;
        tokenInfo.metadata = bytes(metadata);

        (int256 responseCode) = HederaTokenService.updateTokenInfo(token, tokenInfo);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function updateTokenKeys(address token, bytes memory ed25519) public {
        IHederaTokenService.TokenKey[] memory keys = new IHederaTokenService.TokenKey[](1);
        keys[0] = getSingleKey(KeyType.ADMIN, KeyType.METADATA, KeyValueType.ED25519, ed25519); //metadata 7

        (int256 responseCode) = HederaTokenService.updateTokenKeys(token, keys);
        require(responseCode == HederaResponseCodes.SUCCESS);
    }
}
