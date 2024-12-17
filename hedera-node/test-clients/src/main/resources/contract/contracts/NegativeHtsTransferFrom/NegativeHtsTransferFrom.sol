// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.12;
pragma experimental ABIEncoderV2;

import "./HederaTokenService.sol";

contract NegativeHtsTransferFrom is HederaTokenService {

    function transferFromUnderflowAmountValue(address token, address from, address to) external {
        (bool success, bytes memory result) = precompileAddress.call(
            abi.encodeWithSelector(IHederaTokenService.transferFrom.selector, token, from, to, -1));

        require(success);
    }

    function transferFromWithOverflowAmountValue(address token, address from, address to) external {
        (bool success, bytes memory result) = precompileAddress.call(
            abi.encodeWithSelector(IHederaTokenService.transferFrom.selector, token, from, to, type(uint256).max + 1));

        require(success);
    }

}
