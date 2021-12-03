// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.9;

import "./IHederaTokenService.sol";
import "./HederaResponseCodes.sol";

contract HelloWorldMint {
    address constant precompileAddress = address(0x167);

    address tokenAddress;

    constructor(address _tokenAddress) {
        tokenAddress = _tokenAddress;
    }

    function mint() public {
        int response = mintToken(tokenAddress, 0, new bytes(32));
        if (response != 22) {
            revert();
        }
    }

    function mintToken(address token, uint64 amount, bytes memory metadata) internal returns (int responseCode) {
        (bool success, bytes memory result) = precompileAddress.delegatecall(
            abi.encodeWithSelector(IHederaTokenService.mintToken.selector, token, amount, metadata));
        responseCode = success ? abi.decode(result, (int32)) : HederaResponseCodes.UNKNOWN;
    }
}
