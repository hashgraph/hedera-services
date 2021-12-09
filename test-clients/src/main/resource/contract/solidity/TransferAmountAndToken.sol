// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.6.12;
import "./HederaTokenService.sol";
import "./HederaResponseCodes.sol";

contract TransferAmountAndToken is HederaTokenService {

    address tokenAddress;

    constructor(address _tokenAddress) public {
        tokenAddress = _tokenAddress;
    }

    function depositTokens(int64 amount) public {
        int response = HederaTokenService.transferToken(tokenAddress, msg.sender, address(this), amount);

        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Deposit Failed");
        }
    }

    function transferToAddress(address _address, int64 _tokenAmount) public {

        int response = HederaTokenService.transferToken(tokenAddress, address(this), _address, _tokenAmount);

        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Token transfer failed");
        }

        int response2 = HederaTokenService.transferToken(tokenAddress, address(this), _address, _tokenAmount);

        if (response2 != HederaResponseCodes.SUCCESS) {
            revert ("Token transfer failed");
        }
    }


}