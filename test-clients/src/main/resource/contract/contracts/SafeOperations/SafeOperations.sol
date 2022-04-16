// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.6.12;
pragma experimental ABIEncoderV2;

import "./SafeHederaTokenService.sol";

contract SafeOperationsContract is SafeHederaTokenService {
    function safeTokenAssociate(address sender, address tokenAddress) external {
        SafeHederaTokenService.safeAssociateToken(sender, tokenAddress);
    }
    function safeTokenDissociate(address sender, address tokenAddress) external {
        SafeHederaTokenService.safeDissociateToken(sender, tokenAddress);
    }
    function safeTokensAssociate(address account, address[] memory tokens) external {
        SafeHederaTokenService.safeAssociateTokens(account, tokens);
    }
    function safeTokensDissociate(address account, address[] memory tokens) external {
        SafeHederaTokenService.safeDissociateTokens(account, tokens);
    }
    function safeTokensTransfer(address token, address[] memory accountIds, int64[] memory amounts) external {
        SafeHederaTokenService.safeTransferTokens(token, accountIds, amounts);
    }
    function safeNFTsTransfer(address token, address[] memory sender, address[] memory receiver, int64[] memory serialNumber) external {
        SafeHederaTokenService.safeTransferNFTs(token, sender, receiver, serialNumber);
    }
    function safeTokenTransfer(address token, address sender, address receiver, int64 amount) external {
        SafeHederaTokenService.safeTransferToken(token, sender, receiver, amount);
    }
    function safeNFTTransfer(address token, address sender, address receiver, int64 serialNum) external {
        SafeHederaTokenService.safeTransferNFT(token, sender, receiver, serialNum);
    }
    function safeTransferCrypto(IHederaTokenService.TokenTransferList[] memory tokenTransfers) external {
        SafeHederaTokenService.safeCryptoTransfer(tokenTransfers);
    }
    function safeTokenMint(address token, uint64 amount, bytes[] memory metadata) external
    returns (uint64 newTotalSupply, int[] memory serialNumbers)
    {
        (newTotalSupply, serialNumbers) = SafeHederaTokenService.safeMintToken(token, amount, metadata);
    }
    function safeTokenBurn(address token, uint64 amount, int64[] memory serialNumbers) external
    returns (uint64 newTotalSupply)
    {
        (newTotalSupply) = SafeHederaTokenService.safeBurnToken(token, amount, serialNumbers);
    }
}
