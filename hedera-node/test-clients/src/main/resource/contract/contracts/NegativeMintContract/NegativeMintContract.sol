// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.12;
pragma experimental ABIEncoderV2;

import "./HederaTokenService.sol";

contract NegativeMintContract is HederaTokenService {

    address constant HTS_SYSTEM_CONTRACT = address(0x167);

    int128 constant TOO_BIG_VALUE = 78900000000000000000;
    int128 constant TOO_BIG_NEGATIVE_VALUE = -78900000000000000000;
    int32 constant INVALID_ADDRESS = 12300;

    function mintToken(bytes[] memory metadata, address tokenAddress, int64 amount) external {
        (bool success, bytes memory result) = precompileAddress.call(
            abi.encodeWithSelector(IHederaTokenService.mintToken.selector,
                tokenAddress, amount, metadata));

        require(success);
    }

    function mintExtremeValue(bytes[] memory metadata, bool useNegative, address tokenAddress) external {
        int128 amount = useNegative ? TOO_BIG_NEGATIVE_VALUE : TOO_BIG_VALUE;
        (bool success, bytes memory result) = precompileAddress.call(
            abi.encodeWithSelector(IHederaTokenService.mintToken.selector,
                tokenAddress, amount, metadata));

        require(success);
    }

    function mintInvalidAddressType(bytes[] memory metadata, int64 amount) external {
        int32 invalidAddress = 12300;
        (bool success, bytes memory result) = precompileAddress.call(
            abi.encodeWithSelector(IHederaTokenService.mintToken.selector,
                INVALID_ADDRESS, amount, metadata));

        require(success);
    }

}