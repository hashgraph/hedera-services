// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.12;
pragma experimental ABIEncoderV2;

import "./HederaTokenService.sol";
import "./HederaResponseCodes.sol";
import "./ServiceContract.sol";

contract DelegateContract is HederaTokenService {

    ServiceContract serviceContract;

    constructor(address serviceContractAddress) public {
        serviceContract = ServiceContract(serviceContractAddress);
    }


    function transferDelegateCall(address token, address sender, address receiver, int64 serialNum) external {
        (bool success, bytes memory result) = address(serviceContract).delegatecall(abi.encodeWithSignature("nftTransfer(address,address,address,int64)", token, sender, receiver, serialNum));
        if (!success) {
            revert("Delegate transfer call failed!");
        }
    }


    function burnDelegateCall(address token, int64 amount, int64[] memory serialNumbers) external {
        (bool success, bytes memory result) = address(serviceContract).delegatecall(abi.encodeWithSignature("tokenBurn(address,int64,int64[])", token, amount, serialNumbers));
        if (!success) {
            revert("Delegate burn call failed!");
        }
    }

    function mintDelegateCall(address token, int64 amount) external {
        (bool success, bytes memory result) = address(serviceContract).delegatecall(abi.encodeWithSignature("tokenMint(address,int64)", token, amount));
        if (!success) {
            revert("Delegate mint call failed!");
        }
    }
}
