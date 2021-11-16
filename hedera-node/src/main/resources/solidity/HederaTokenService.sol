// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.4.9 <0.7.0;
pragma experimental ABIEncoderV2;

import "./HederaResponseCodes.sol";

library HederaTokenService {

    address constant precompileAddress = address(0x167);

    function cryptoTransfer(IHederaTokenService.TokenTransferList[] calldata tokenTransfers) internal returns (int responseCode) {
        (bool success, bytes memory result) = precompileAddress.delegatecall(
            abi.encodeWithSelector(IHederaTokenService.cryptoTransfer.selector,
            tokenTransfers));
        int64 response = success ? abi.decode(result, (int64)) : 20;
        //FIXME HederaResponseCodes.UNKNOWN;
        return response;
    }

    function mintToken(address token, uint64 amount, bytes calldata metadata) internal returns (int responseCode) {
        (bool success, bytes memory result) = precompileAddress.delegatecall(
            abi.encodeWithSelector(IHederaTokenService.mintToken.selector,
            token, amount, metadata));
        int64 response = success ? abi.decode(result, (int64)) : 20;
        //FIXME HederaResponseCodes.UNKNOWN;
        return response;
    }

    function burnToken(address token, uint64 amount, int64[] calldata serialNumbers) internal returns (int responseCode) {
        (bool success, bytes memory result) = precompileAddress.delegatecall(
            abi.encodeWithSelector(IHederaTokenService.burnToken.selector,
            token, amount, serialNumbers));
        int64 response = success ? abi.decode(result, (int64)) : 20;
        //FIXME HederaResponseCodes.UNKNOWN;
        return response;
    }

    function associateTokens(address account, address[] calldata tokens) internal returns (int responseCode) {
        (bool success, bytes memory result) = precompileAddress.delegatecall(
            abi.encodeWithSelector(IHederaTokenService.associateTokens.selector,
            account, tokens));
        int64 response = success ? abi.decode(result, (int64)) : 20;
        //FIXME HederaResponseCodes.UNKNOWN;
        return response;
    }

    function associateToken(address account, address token) internal returns (int responseCode) {
        (bool success, bytes memory result) = precompileAddress.delegatecall(
            abi.encodeWithSelector(IHederaTokenService.associateToken.selector,
            account, token));
        int64 response = success ? abi.decode(result, (int64)) : 20;
        //FIXME HederaResponseCodes.UNKNOWN;
        return response;
    }

    function dissociateTokens(address account, address[] calldata tokens) internal returns (int responseCode) {
        (bool success, bytes memory result) = precompileAddress.delegatecall(
            abi.encodeWithSelector(IHederaTokenService.dissociateTokens.selector,
            account, tokens));
        int64 response = success ? abi.decode(result, (int64)) : 20;
        //FIXME HederaResponseCodes.UNKNOWN;
        return response;
    }

    function dissociateToken(address account, address token) internal returns (int responseCode) {
        (bool success, bytes memory result) = precompileAddress.delegatecall(
            abi.encodeWithSelector(IHederaTokenService.dissociateToken.selector,
            account, token));
        int64 response = success ? abi.decode(result, (int64)) : 20;
        //FIXME HederaResponseCodes.UNKNOWN;
        return response;
    }

    function transferTokens(address token, address[] calldata accountId, int64[] calldata amount) internal returns (int responseCode) {
        (bool success, bytes memory result) = precompileAddress.delegatecall(
            abi.encodeWithSelector(IHederaTokenService.transferTokens.selector,
            token, accountId, amount));
        int64 response = success ? abi.decode(result, (int64)) : 20;
        //FIXME HederaResponseCodes.UNKNOWN;
        return response;
    }

    function transferNFTs(address token, address[] calldata sender, address[] calldata receiver, int64[] calldata serialNumber) internal returns (int responseCode) {
        (bool success, bytes memory result) = precompileAddress.delegatecall(
            abi.encodeWithSelector(IHederaTokenService.transferNFTs.selector,
            token, sender, receiver, serialNumber));
        int64 response = success ? abi.decode(result, (int64)) : 20;
        //FIXME HederaResponseCodes.UNKNOWN;
        return response;
    }

    function transferToken(address token, address sender, address recipient, int64 amount) internal returns (int responseCode) {
        (bool success, bytes memory result) = precompileAddress.delegatecall(
            abi.encodeWithSelector(IHederaTokenService.transferToken.selector,
            token, sender, recipient, amount));
        int64 response = success ? abi.decode(result, (int64)) : 20;
        //FIXME HederaResponseCodes.UNKNOWN;
        return response;
    }

    function transferNFT(address token,  address sender, address recipient, int64 serialNum) internal returns (int responseCode) {
        (bool success, bytes memory result) = precompileAddress.delegatecall(
            abi.encodeWithSelector(IHederaTokenService.transferNFT.selector,
            token, sender, recipient, serialNum));
        int64 response = success ? abi.decode(result, (int64)) : 20;
        //FIXME HederaResponseCodes.UNKNOWN;
        return response;
    }
}