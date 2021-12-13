// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.6.12;

import "./HederaTokenService.sol";

contract MintContract is HederaTokenService {

    address tokenAddress;

    constructor(address _tokenAddress) public {
        tokenAddress = _tokenAddress;
    }

    function mintFungibleToken(uint64 amount) external {
        HederaTokenService.mintToken(tokenAddress, amount, new bytes(0));
    }

    function mintNonFungibleToken(bytes calldata metadata) external {
        HederaTokenService.mintToken(tokenAddress, 0, metadata);
    }
}