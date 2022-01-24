// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.6.12;
pragma experimental ABIEncoderV2;

import "./SafeHTS.sol";

contract SafeOperationsContract {
    using SafeHTS for IHederaTokenService;

    function safeTokenAssociate(address sender, address tokenAddress) external {
        IHederaTokenService(tokenAddress).safeAssociateToken(sender);
    }

    function safeTokenDissociate(address sender, address tokenAddress) external {
        IHederaTokenService(tokenAddress).safeDissociateToken(sender);
    }

    function safeTokensAssociate(address account, address[] memory tokens) external {
        SafeHTS.safeAssociateTokens(account, tokens);
    }

    function safeTokensDissociate(address account, address[] memory tokens) external {
        SafeHTS.safeDissociateTokens(account, tokens);
    }

    function safeTokensTransfer(address token, address[] memory accountIds, int64[] memory amounts) external {
        IHederaTokenService(token).safeTransferTokens(accountIds, amounts);
    }

    function safeNFTsTransfer(address token, address[] memory sender, address[] memory receiver, int64[] memory serialNumber) external {
        IHederaTokenService(token).safeTransferNFTs(sender, receiver, serialNumber);
    }

    function safeTokenTransfer(address token, address sender, address receiver, int64 amount) external {
        IHederaTokenService(token).safeTransferToken(sender, receiver, amount);
    }

    function safeNFTTransfer(address token, address sender, address receiver, int64 serialNum) external {
        IHederaTokenService(token).safeTransferNFT(sender, receiver, serialNum);
    }

    function safeTransferCrypto(IHederaTokenService.TokenTransferList[] memory tokenTransfers) external {
        SafeHTS.safeCryptoTransfer(tokenTransfers);
    }

    function safeTokenMint(address token, uint64 amount, bytes[] memory metadata) external
    returns (uint64 newTotalSupply, int64[] memory serialNumbers)
    {
        (newTotalSupply, serialNumbers) = IHederaTokenService(token).safeMintToken(amount, metadata);
    }

    function safeTokenBurn(address token, uint64 amount, int64[] memory serialNumbers) external
    returns (uint64 newTotalSupply)
    {
        (newTotalSupply) = IHederaTokenService(token).safeBurnToken(amount, serialNumbers);
    }
}
