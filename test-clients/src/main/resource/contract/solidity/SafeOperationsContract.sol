// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.6.12;
pragma experimental ABIEncoderV2;

import "./SafeHTS.sol";

contract SafeOperationsContract {
    function safeTokenAssociate(address sender, address tokenAddress) external {
        SafeHTS.safeAssociateToken(sender, tokenAddress);
    }
    function safeTokenDissociate(address sender, address tokenAddress) external {
        SafeHTS.safeDissociateToken(sender, tokenAddress);
    }
    function safeTokensAssociate(address account, address[] memory tokens) external {
        SafeHTS.safeAssociateTokens(account, tokens);
    }
    function safeTokensDissociate(address account, address[] memory tokens) external {
        SafeHTS.safeDissociateTokens(account, tokens);
    }
    function safeTokensTransfer(address token, address[] memory accountIds, int64[] memory amounts) external {
        SafeHTS.safeTransferTokens(token, accountIds, amounts);
    }
    function safeNFTsTransfer(address token, address[] memory sender, address[] memory receiver, int64[] memory serialNumber) external {
        SafeHTS.safeTransferNFTs(token, sender, receiver, serialNumber);
    }
    function safeTokenTransfer(address token, address sender, address receiver, int64 amount) external {
        SafeHTS.safeTransferToken(token, sender, receiver, amount);
    }
    function safeNFTTransfer(address token, address sender, address receiver, int64 serialNum) external {
        SafeHTS.safeTransferNFT(token, sender, receiver, serialNum);
    }
    function safeTransferCrypto(IHederaTokenService.TokenTransferList[] memory tokenTransfers) external {
        SafeHTS.safeCryptoTransfer(tokenTransfers);
    }
    function safeTokenMint(address token, uint64 amount, bytes[] memory metadata) external
    returns (uint64 newTotalSupply, int64[] memory serialNumbers)
    {
        (newTotalSupply, serialNumbers) = SafeHTS.safeMintToken(token, amount, metadata);
    }
    function safeTokenBurn(address token, uint64 amount, int64[] memory serialNumbers) external
    returns (uint64 newTotalSupply)
    {
        (newTotalSupply) = SafeHTS.safeBurnToken(token, amount, serialNumbers);
    }
}
