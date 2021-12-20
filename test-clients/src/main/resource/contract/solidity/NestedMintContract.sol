// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.6.12;

import "./HederaTokenService.sol";

contract NestedMintContract is HederaTokenService {

    MintContract mintContract;
    address tokenAddress;

    constructor(address _mintContractAddress, address _tokenAddress) public {
        mintContract = MintContract(_mintContractAddress);
        tokenAddress = _tokenAddress;
    }

    function sendNFTAfterMint(address sender, address recipient, bytes calldata metadata, int64 serialNumber) external {
        mintContract.mintNonFungibleTokenWithAddress(tokenAddress, metadata);
        HederaTokenService.associateToken(sender, tokenAddress);
        HederaTokenService.associateToken(recipient, tokenAddress);
        int response = HederaTokenService.transferNFT(tokenAddress, sender, recipient, serialNumber);
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Non Fungible transfer failed!");
        }
    }

    function revertMintAfterFailedAssociate(address accountToAssociate, bytes calldata metadata) external {
        mintContract.mintNonFungibleTokenWithAddress(tokenAddress, metadata);
        int response = HederaTokenService.associateToken(accountToAssociate, accountToAssociate);
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Associate of NFT failed!");
        }
    }
}


contract MintContract is HederaTokenService {

    function mintNonFungibleTokenWithAddress(address tokenAddress, bytes calldata metadata) external {
        int response = HederaTokenService.mintToken(tokenAddress, 0, metadata);
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Non Fungible mint failed!");
        }
    }
}