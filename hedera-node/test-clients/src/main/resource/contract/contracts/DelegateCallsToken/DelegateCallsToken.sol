// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.6.0 <0.9.0;
pragma experimental ABIEncoderV2;

import "./HederaTokenService.sol";
import "./IHederaTokenService.sol";
import "./HederaResponseCodes.sol";

contract DelegateCallsToken is HederaTokenService {

    function mintTokenCall(uint64 amount, address tokenAddress) public
        returns (bool success, bytes memory result) {
    (success, result) = precompileAddress.call(
            abi.encodeWithSelector(IHederaTokenService.mintToken.selector,
            tokenAddress, amount, new bytes[](0)));
    }

    function mintTokenDelegateCall(uint64 amount, address tokenAddress, bytes[] memory metadata) public
    returns (bool success, bytes memory result) {
        (success, result) = precompileAddress.delegatecall(
            abi.encodeWithSelector(IHederaTokenService.mintToken.selector,
                tokenAddress, amount, metadata));

        int mintResponse = success
            ? abi.decode(result, (int32))
            : (HederaResponseCodes.UNKNOWN);

        if (mintResponse != HederaResponseCodes.SUCCESS) {
            revert ("Token mint failed");
        }
    }

    function burnTokenDelegateCall(uint64 amount, address tokenAddress, int64[] memory serialNumbers) public
    returns (bool success, bytes memory result) {
        (success, result) = precompileAddress.delegatecall(
            abi.encodeWithSelector(IHederaTokenService.burnToken.selector,
                tokenAddress, amount, serialNumbers));

        int burnResponse = success
            ? abi.decode(result, (int32))
            : (HederaResponseCodes.UNKNOWN);

        if (burnResponse != HederaResponseCodes.SUCCESS) {
            revert ("Token burn failed");
        }
    }
}