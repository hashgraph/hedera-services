// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;
pragma experimental ABIEncoderV2;

import "./HederaTokenService.sol";

abstract contract SafeHederaTokenService is HederaTokenService {

    function safeCryptoTransfer(IHederaTokenService.TokenTransferList[] memory tokenTransfers) internal {
        int responseCode;
        (responseCode) = HederaTokenService.cryptoTransfer(tokenTransfers);
        require(responseCode == HederaResponseCodes.SUCCESS, "Safe crypto transfer failed!");
    }

    function safeMintToken(address token, uint64 amount, bytes[] memory metadata) internal
    returns (uint64 newTotalSupply, int[] memory serialNumbers) {
        int responseCode;
        (responseCode, newTotalSupply, serialNumbers) = HederaTokenService.mintToken(token, amount, metadata);

        require(responseCode == HederaResponseCodes.SUCCESS, "Safe mint failed!");
    }

    function safeBurnToken(address token, uint64 amount, int64[] memory serialNumbers) internal
    returns (uint64 newTotalSupply)
    {
        int responseCode;
        (responseCode, newTotalSupply) = HederaTokenService.burnToken(token, amount, serialNumbers);
        require(responseCode == HederaResponseCodes.SUCCESS, "Safe burn failed!");
    }

    function safeAssociateTokens(address account, address[] memory tokens) internal {
        int responseCode;
        (responseCode) = HederaTokenService.associateTokens(account, tokens);
        require(responseCode == HederaResponseCodes.SUCCESS, "Safe multiple associations failed!");
    }

    function safeAssociateToken(address account, address token) internal {
        int responseCode;
        (responseCode) = HederaTokenService.associateToken(account, token);
        require(responseCode == HederaResponseCodes.SUCCESS, "Safe single association failed!");
    }

    function safeDissociateTokens(address account, address[] memory tokens) internal {
        int responseCode;
        (responseCode) = HederaTokenService.dissociateTokens(account, tokens);
        require(responseCode == HederaResponseCodes.SUCCESS, "Safe multiple dissociations failed!");
    }

    function safeDissociateToken(address account, address token) internal {
        int responseCode;
        (responseCode) = HederaTokenService.dissociateToken(account, token);
        require(responseCode == HederaResponseCodes.SUCCESS, "Safe single dissociation failed!");
    }

    function safeTransferTokens(address token, address[] memory accountIds, int64[] memory amounts) internal {
        int responseCode;
        (responseCode) = HederaTokenService.transferTokens(token, accountIds, amounts);
        require(responseCode == HederaResponseCodes.SUCCESS, "Safe tokens transfer failed!");
    }

    function safeTransferNFTs(address token, address[] memory sender, address[] memory receiver, int64[] memory serialNumber) internal {
        int responseCode;
        (responseCode) = HederaTokenService.transferNFTs(token, sender, receiver, serialNumber);
        require(responseCode == HederaResponseCodes.SUCCESS, "Safe NFTs transfer failed!");
    }

    function safeTransferToken(address token, address sender, address receiver, int64 amount) internal {
        int responseCode;
        (responseCode) = HederaTokenService.transferToken(token, sender, receiver, amount);
        require(responseCode == HederaResponseCodes.SUCCESS, "Safe token transfer failed!");
    }

    function safeTransferNFT(address token, address sender, address receiver, int64 serialNum) internal {
        int responseCode;
        (responseCode) = HederaTokenService.transferNFT(token, sender, receiver, serialNum);
        require(responseCode == HederaResponseCodes.SUCCESS, "Safe NFT transfer failed!");
    }
}
