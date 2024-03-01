// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.12;
pragma experimental ABIEncoderV2;

import "./HederaTokenService.sol";

contract NegativeMintContract is HederaTokenService {

    function mintFungibleTokenMAX(address tokenAddress) external {
        HederaTokenService.mintToken(tokenAddress, 78900000000000000000, new bytes[](0));
    }

    function mintNonFungibleTokenMAX(bytes[] memory metadata, address tokenAddress) external {
        HederaTokenService.mintToken(tokenAddress, 78900000000000000000, metadata);
    }

    function mintNonFungibleTokenMIN(bytes[] memory metadata, address tokenAddress) external {
        HederaTokenService.mintToken(tokenAddress, -78900000000000000000, metadata);
    }

    function mintFungibleTokenMIN(bytes[] memory metadata, address tokenAddress) external {
        HederaTokenService.mintToken(tokenAddress, -78900000000000000000, metadata);
    }

    function mintFungibleToken(bytes[] memory metadata, address tokenAddress) external {
        HederaTokenService.mintToken(tokenAddress, 10, metadata);
    }

    function mintNFT(bytes[] memory metadata, address tokenAddress) external {
        HederaTokenService.mintToken(tokenAddress, 10, metadata);
    }

}