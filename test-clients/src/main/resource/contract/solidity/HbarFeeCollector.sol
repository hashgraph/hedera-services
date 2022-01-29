// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.6.0 <0.9.0;

import "./hip-206/HederaTokenService.sol";
import "./hip-206/IHederaTokenService.sol";
import "./hip-206/HederaResponseCodes.sol";

contract HbarFeeCollector is HederaTokenService {

    NestedHTSTransferer nestedHTSTransferer;

    constructor(address transfererContractAddress) public {
        nestedHTSTransferer = NestedHTSTransferer(transfererContractAddress);
    }

    function feeDistributionAfterTransfer(address tokenAddress, address sender, address tokenReceiver, int64 amount,
        address payable hbarReceiver) external {
        nestedHTSTransferer.transfer(tokenAddress, sender, tokenReceiver, amount);
        hbarReceiver.transfer(100);
    }
}

contract NestedHTSTransferer is HederaTokenService {

    function transfer(address tokenAddress, address sender, address receiver, int64 amount) external {
        int response = HederaTokenService.transferToken(tokenAddress, sender, receiver, amount);
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Transfer of tokens failed");
        }
    }
}