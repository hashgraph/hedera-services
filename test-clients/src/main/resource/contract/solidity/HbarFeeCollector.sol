// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.6.0 <0.9.0;

import "./hip-206/HederaTokenService.sol";
import "./hip-206/IHederaTokenService.sol";
import "./hip-206/HederaResponseCodes.sol";

contract HbarFeeCollector is HederaTokenService {

    NestedHTSTransferrer nestedHTSTransferrer;

    constructor(address transferrerContractAddress) {
        nestedHTSTransferrer = NestedHTSTransferrer(transferrerContractAddress);
    }

    function feeDistributionAfterTransfer(
        address _tokenAddress,
        address _sender,
        address _tokenReceiver,
        address payable _hbarReceiver,
        int64 _tokenAmount,
        uint256 _hbarAmount) external {
        nestedHTSTransferrer.transfer(_tokenAddress, _sender, _tokenReceiver, _tokenAmount);
        _hbarReceiver.transfer(_hbarAmount);
    }
}

contract NestedHTSTransferrer is HederaTokenService {

    function transfer(
        address _tokenAddress,
        address _sender,
        address _receiver,
        int64 _amount) external {
        int response = HederaTokenService.transferToken(_tokenAddress, _sender, _receiver, _amount);
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Transfer of tokens failed");
        }
    }
}