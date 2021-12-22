// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.6.12;
pragma experimental ABIEncoderV2;

import "./hip-206/IHederaTokenService.sol";
import "./hip-206/HederaResponseCodes.sol";

contract HelloWorldMint {
    address constant precompileAddress = address(0x167);

    address tokenAddress;

    constructor(address _tokenAddress) public {
        tokenAddress = _tokenAddress;
    }

    function mint() public {
        int response = mintToken(tokenAddress, 0, new bytes[](1));
        if (response != 22) {
            revert();
        }
    }

    function brrr(uint64 amount) public {
        int response = mintToken(tokenAddress, amount, new bytes[](0));
        if (response != 22) {
            revert();
        }
    }

    function mintToken(address token, uint64 amount, bytes[] memory metadata) internal returns (int responseCode) {
        (bool success, bytes memory result) = precompileAddress.delegatecall(
            abi.encodeWithSelector(IHederaTokenService.mintToken.selector, token, amount, metadata));
        responseCode = success ? abi.decode(result, (int32)) : int8(21);
    }
}