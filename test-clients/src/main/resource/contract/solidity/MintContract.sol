// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.6.12;

import "./HederaTokenService.sol";

contract MintContract is HederaTokenService {

    address tokenAddress;

    constructor(address _tokenAddress) public {
        tokenAddress = _tokenAddress;
    }

    function mintFungibleToken(uint64 amount) external {
        int response = HederaTokenService.mintToken(tokenAddress, amount, new bytes(0));
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Fungible mint failed!");
        }
    }

    function mintNonFungibleToken(bytes calldata metadata) external {
        int response = HederaTokenService.mintToken(tokenAddress, 0, metadata);
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Non fungible mint failed!");
        }
    }

    function mintNonFungibleTokenWithAddress(address _tokenAddress, bytes calldata metadata) external {
        int response = HederaTokenService.mintToken(_tokenAddress, 0, metadata);
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Non fungible mint failed!");
        }
    }

    function revertMintAfterFailedMint(address sender, address recipient, int64 amount) external {
        HederaTokenService.transferToken(tokenAddress, sender, recipient, amount);
        int response = HederaTokenService.mintToken(tokenAddress, 0, '');
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Mint of fungible token failed!");
        }
    }
}