// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.6.0 <0.9.0;

import "./hip-206/HederaTokenService.sol";
import "./hip-206/IHederaTokenService.sol";
import "./hip-206/HederaResponseCodes.sol";

contract SimpleContractKeyInteraction is HederaTokenService {
    function transferNFTCall(address token, address sender, address receiver, int64 serialNum) internal returns (int responseCode) {
        (bool success, bytes memory result) = precompileAddress.call(
            abi.encodeWithSelector(IHederaTokenService.transferNFT.selector,
            token, sender, receiver, serialNum));
        responseCode = success ? abi.decode(result, (int32)) : HederaResponseCodes.UNKNOWN;
    }
}