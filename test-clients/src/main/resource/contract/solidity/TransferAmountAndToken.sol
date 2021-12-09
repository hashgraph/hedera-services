// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.6.12;
import "./HederaTokenService.sol";
import "./HederaResponseCodes.sol";

contract TransferAmountAndToken is HederaTokenService {

    address tokenAddress;

    constructor(address _tokenAddress) public {
        tokenAddress = _tokenAddress;
    }

    function depositTokens(int64 serialNum) public {
        int response = HederaTokenService.transferNFT(tokenAddress, msg.sender, address(this), serialNum);

        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Deposit Failed");
        }
    }

    function transferToAddress(address _address, int64 serialNum) public {

        int response = HederaTokenService.transferNFT(tokenAddress, address(this), _address, serialNum);

        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Token transfer failed");
        }

    }


}