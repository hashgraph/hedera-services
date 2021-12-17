// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.6.0 <0.9.0;

import "./hip-206/HederaTokenService.sol";
import "./hip-206/IHederaTokenService.sol";
import "./hip-206/HederaResponseCodes.sol";

contract VersatileTransfers is HederaTokenService {
    function distributeTokens(address tokenAddress, address[] calldata accounts, int64[] calldata amounts) public {
        int response = HederaTokenService.transferTokens(tokenAddress, accounts, amounts);
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Transfer of tokens failed");
        }
    }

    function transferNft(address token, address sender, address receiver, int64 serialNum) external {
        int response = HederaTokenService.transferNFT(token, sender, receiver, serialNum);
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Transfer of NFT failed");
        }
    }

    function transferNfts(address token, address[] memory sender, address[] memory receiver, int64[] memory serialNumber) external {
        int response = HederaTokenService.transferNFTs(token, sender, receiver, serialNumber);
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Transfer of NFTs failed");
        }
    }
}