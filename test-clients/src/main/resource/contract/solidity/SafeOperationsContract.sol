// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.6.12;
pragma experimental ABIEncoderV2;

import "./SafeHTS.sol";

contract SafeOperationsContract {
    using SafeHTS for IHTS;

    function safeTokenAssociate(address sender, address tokenAddress) external {
        IHTS(tokenAddress).safeAssociateToken(sender);
    }

    function safeTokenDissociate(address sender, address tokenAddress) external {
        IHTS(tokenAddress).safeDissociateToken(sender);
    }

    function safeTokensTransfer(address token, address[] memory accountIds, int64[] memory amounts) external {
        IHTS(token).safeTransferTokens(accountIds, amounts);
    }

    function safeNFTsTransfer(address token, address[] memory sender, address[] memory receiver, int64[] memory serialNumber) external {
        IHTS(token).safeTransferNFTs(sender, receiver, serialNumber);
    }

    function safeTokenTransfer(address token, address sender, address receiver, int64 amount) external {
        IHTS(token).safeTransferToken(sender, receiver, amount);
    }

    function safeNFTTransfer(address token, address sender, address receiver, int64 serialNum) external {
        IHTS(token).safeTransferNFT(sender, receiver, serialNum);
    }

    function safeTokenMint(address token, uint64 amount, bytes[] memory metadata) external
    returns (uint64 newTotalSupply, int64[] memory serialNumbers)
    {
        (newTotalSupply, serialNumbers) = IHTS(token).safeMintToken(amount, metadata);
    }

    function safeTokenBurn(address token, uint64 amount, int64[] memory serialNumbers) external
    returns (uint64 newTotalSupply)
    {
        (newTotalSupply) = IHTS(token).safeBurnToken(amount, serialNumbers);
    }

    function safeTokensAssociate(address account, address[] memory tokens) external {
        SafeHTS.safeAssociateTokens(account, tokens);
    }

    function safeTokensDissociate(address account, address[] memory tokens) external {
        SafeHTS.safeDissociateTokens(account, tokens);
    }

    function safeTransferCrypto(IHTS.TokenTransferList[] memory tokenTransfers) external {
        SafeHTS.safeCryptoTransfer(tokenTransfers);
    }
}
