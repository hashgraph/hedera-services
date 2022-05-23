// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.6.12;
pragma experimental ABIEncoderV2;

import "./HederaTokenService.sol";
import "./MintNFTContract.sol";

contract NestedMintContract is HederaTokenService {

    address mintNFTContractAddress;
    address tokenAddress;

    constructor(address _mintNFTContractAddress, address _tokenAddress) public {
        mintNFTContractAddress = _mintNFTContractAddress;
        tokenAddress = _tokenAddress;
    }

    function sendNFTAfterMint(address sender, address recipient, bytes[] memory metadata, int64 serialNumber) external {
        (bool success, bytes memory result) = mintNFTContractAddress.delegatecall(abi.encodeWithSelector
            (MintNFTContract.mintNonFungibleTokenWithAddress.selector, tokenAddress, metadata));
        int response = HederaTokenService.transferNFT(tokenAddress, sender, recipient, serialNumber);
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Non Fungible transfer failed!");
        }
    }

    function revertMintAfterFailedAssociate(address accountToAssociate, bytes[] memory metadata) external {
        (bool success, bytes memory result) = mintNFTContractAddress.delegatecall(abi.encodeWithSelector
            (MintNFTContract.mintNonFungibleTokenWithAddress.selector, tokenAddress, metadata));
        int response = HederaTokenService.associateToken(accountToAssociate, accountToAssociate);
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Associate of NFT failed!");
        }
    }
}
