// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.6.12;

import "./HederaTokenService.sol";

contract TransferNftContract is HederaTokenService {

    function transferNft(address token, address sender, address receiver, int64 serialNum) external {
        HederaTokenService.transferNFT(token, sender, receiver, serialNum);
    }

    function transferNfts(address token, address[] memory sender, address[] memory receiver, int64[] memory serialNumber) external {
        HederaTokenService.transferNFTs(token, sender, receiver, serialNumber);
    }
}


   