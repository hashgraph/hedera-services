// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;
pragma experimental ABIEncoderV2;

import "./IHTS.sol";
import "./HederaResponseCodes.sol";

library SafeHTS {

    address constant precompileAddress = address(0x167);

    function safeMintToken(IHTS token, uint64 amount, bytes[] memory metadata) internal
    returns (uint64 newTotalSupply, int64[] memory serialNumbers) {
        int32 responseCode;
        (bool success, bytes memory result) = precompileAddress.delegatecall(
            abi.encodeWithSelector(IHTS.mintToken.selector,
            token, amount, metadata));
        (responseCode, newTotalSupply, serialNumbers) =
        success
        ? abi.decode(result, (int32, uint64, int64[]))
        : (HederaResponseCodes.UNKNOWN, 0, new int64[](0));
        require(responseCode == HederaResponseCodes.SUCCESS, "Safe mint failed!");
    }

    function safeBurnToken(IHTS token, uint64 amount, int64[] memory serialNumbers) internal
    returns (uint64 newTotalSupply)
    {
        int32 responseCode;
        (bool success, bytes memory result) = precompileAddress.call(
            abi.encodeWithSelector(IHTS.burnToken.selector,
            token, amount, serialNumbers));
        (responseCode, newTotalSupply) =
        success
        ? abi.decode(result, (int32, uint64))
        : (HederaResponseCodes.UNKNOWN, 0);
        require(responseCode == HederaResponseCodes.SUCCESS, "Safe burn failed!");
    }

    function safeAssociateToken(IHTS token, address account) internal {
        int32 responseCode;
        (bool success, bytes memory result) = precompileAddress.call(
            abi.encodeWithSelector(IHTS.associateToken.selector,
            account, token));
        responseCode = success ? abi.decode(result, (int32)) : HederaResponseCodes.UNKNOWN;
        require(responseCode == HederaResponseCodes.SUCCESS, "Safe single association failed!");
    }

    function safeDissociateToken(IHTS token, address account) internal {
        int32 responseCode;
        (bool success, bytes memory result) = precompileAddress.call(
            abi.encodeWithSelector(IHTS.dissociateToken.selector,
            account, token));
        responseCode = success ? abi.decode(result, (int32)) : HederaResponseCodes.UNKNOWN;
        require(responseCode == HederaResponseCodes.SUCCESS, "Safe single dissociation failed!");
    }

    function safeTransferTokens(IHTS token, address[] memory accountIds, int64[] memory amounts) internal {
        int32 responseCode;
        (bool success, bytes memory result) = precompileAddress.call(
            abi.encodeWithSelector(IHTS.transferTokens.selector,
            token, accountIds, amounts));
        responseCode = success ? abi.decode(result, (int32)) : HederaResponseCodes.UNKNOWN;
        require(responseCode == HederaResponseCodes.SUCCESS, "Safe tokens transfer failed!");
    }

    function safeTransferNFTs(IHTS token, address[] memory sender, address[] memory receiver, int64[] memory serialNumber) internal {
        int32 responseCode;
        (bool success, bytes memory result) = precompileAddress.call(
            abi.encodeWithSelector(IHTS.transferNFTs.selector,
            token, sender, receiver, serialNumber));
        responseCode = success ? abi.decode(result, (int32)) : HederaResponseCodes.UNKNOWN;
        require(responseCode == HederaResponseCodes.SUCCESS, "Safe NFTs transfer failed!");
    }

    function safeTransferToken(IHTS token, address sender, address receiver, int64 amount) internal {
        int32 responseCode;
        (bool success, bytes memory result) = precompileAddress.call(
            abi.encodeWithSelector(IHTS.transferToken.selector,
            token, sender, receiver, amount));
        responseCode = success ? abi.decode(result, (int32)) : HederaResponseCodes.UNKNOWN;
        require(responseCode == HederaResponseCodes.SUCCESS, "Safe token transfer failed!");
    }

    function safeTransferNFT(IHTS token, address sender, address receiver, int64 serialNumber) internal {
        int32 responseCode;
        (bool success, bytes memory result) = precompileAddress.call(
            abi.encodeWithSelector(IHTS.transferNFT.selector,
            token, sender, receiver, serialNumber));
        responseCode = success ? abi.decode(result, (int32)) : HederaResponseCodes.UNKNOWN;
        require(responseCode == HederaResponseCodes.SUCCESS, "Safe NFT transfer failed!");
    }

    function safeAssociateTokens(address account, address[] memory tokens) internal {
        int32 responseCode;
        (bool success, bytes memory result) = precompileAddress.call(
            abi.encodeWithSelector(IHTS.associateTokens.selector,
            account, tokens));
        responseCode = success ? abi.decode(result, (int32)) : HederaResponseCodes.UNKNOWN;
        require(responseCode == HederaResponseCodes.SUCCESS, "Safe multiple associations failed!");
    }

    function safeDissociateTokens(address account, address[] memory tokens) internal {
        int32 responseCode;
        (bool success, bytes memory result) = precompileAddress.call(
            abi.encodeWithSelector(IHTS.dissociateTokens.selector,
            account, tokens));
        responseCode = success ? abi.decode(result, (int32)) : HederaResponseCodes.UNKNOWN;
        require(responseCode == HederaResponseCodes.SUCCESS, "Safe multiple dissociations failed!");
    }

    function safeCryptoTransfer(IHTS.TokenTransferList[] memory tokenTransfers) internal {
        int32 responseCode;
        (bool success, bytes memory result) = precompileAddress.call(
            abi.encodeWithSelector(IHTS.cryptoTransfer.selector, tokenTransfers));
        responseCode = success ? abi.decode(result, (int32)) : HederaResponseCodes.UNKNOWN;
        require(responseCode == HederaResponseCodes.SUCCESS, "Safe crypto transfer failed!");
    }
}
