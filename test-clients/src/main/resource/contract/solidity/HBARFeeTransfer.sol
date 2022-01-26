// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.6.0 <0.9.0;

import "./hip-206/HederaTokenService.sol";
import "./hip-206/IHederaTokenService.sol";
import "./hip-206/HederaResponseCodes.sol";

contract HBARFeeTransfer is HederaTokenService {
    Transferer transferer;

    constructor(address transfererContractAddress) {
        transferer = Transferer(transfererContractAddress);
    }

    function feeDistributionAfterTransfer(address tokenAddress, address sender, address receiver, int64 amount, address payable feeCollector) external {
        // make this a nested call
        transferer.transfer(tokenAddress, sender, receiver, amount);

        // do hbar transfer
        feeCollector.transfer(100);
    }
}

contract Transferer is HederaTokenService {
    function transfer(address tokenAddress, address sender, address receiver, int64 amount) external {
        int response = HederaTokenService.transferToken(tokenAddress, sender, receiver, amount);
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Transfer of tokens failed");
        }
    }
}