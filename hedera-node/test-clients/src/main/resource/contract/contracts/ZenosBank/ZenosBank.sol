// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.6.0 <0.9.0;

import "./HederaTokenService.sol";
import "./HederaResponseCodes.sol";

contract ZenosBank is HederaTokenService {

    address tokenAddress;

    uint256 lastWithdrawalTime;

    int64 deposited;

    constructor(address _tokenAddress) {
        tokenAddress = _tokenAddress;
    }

    function depositTokens(int64 amount) public {
        int response = HederaTokenService.transferToken(tokenAddress, msg.sender, address(this), amount);
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Deposit Failed");
        }

        deposited += amount;
    }

    function withdrawTokens() external {
        if (block.timestamp <= lastWithdrawalTime) {
            revert("Already withdrew this second");
        }

        int associateResponse = HederaTokenService.associateToken(msg.sender, tokenAddress);
        if (associateResponse != HederaResponseCodes.SUCCESS) {
            revert("Could not associate account");
        }

        depositTokens( -deposited / 2);

        lastWithdrawalTime = block.timestamp;
    }
}
