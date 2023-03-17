// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.12;
import "./HederaTokenService.sol";
import "./HederaResponseCodes.sol";

contract BurnToken is HederaTokenService {

    event BurnedTokenInfo(int64 indexed totalSupply) anonymous;
    address tokenAddress;

    constructor(address _tokenAddress) public {
        tokenAddress = _tokenAddress;
    }

   function burnToken(int64 amount, int64[] memory serialNumbers) public {
        (int response, int64 newTotalSupply) = HederaTokenService.burnToken(tokenAddress, amount, serialNumbers);

        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Token burn failed");
        }
   }

   function burnTokenWithEvent(int64 amount, int64[] memory serialNumbers) public {
        (int response, int64 newTotalSupply) = HederaTokenService.burnToken(tokenAddress, amount, serialNumbers);

        emit BurnedTokenInfo(newTotalSupply);
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Token burn failed");
        }
   }
}