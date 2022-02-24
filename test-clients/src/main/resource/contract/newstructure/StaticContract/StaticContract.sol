// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.6.12;
pragma experimental ABIEncoderV2;

import "./HederaTokenService.sol";
import "./HederaResponseCodes.sol";
import "./ServiceContract.sol";

contract StaticContract is HederaTokenService {

    ServiceContract serviceContract;

    constructor(address serviceContractAddress) public {
        serviceContract = ServiceContract(serviceContractAddress);
    }


    function transferStaticCall(address token, address sender, address receiver, int64 serialNum) external {
        (bool success, bytes memory result) = address(serviceContract).staticcall(abi.encodeWithSignature("nftTransfer(address,address,address,int64)", token, sender, receiver, serialNum));
        if (!success) {
            revert("Static transfer call failed!");
        }
    }


    function burnStaticCall(address token, uint64 amount, int64[] memory serialNumbers) external {
        (bool success, bytes memory result) = address(serviceContract).staticcall(abi.encodeWithSignature("tokenBurn(address,uint64,int64[])", token, amount, serialNumbers));
        if (!success) {
            revert("Static burn call failed!");
        }
    }

    function mintStaticCall(address token, uint64 amount) external {
        (bool success, bytes memory result) = address(serviceContract).staticcall(abi.encodeWithSignature("tokenMint(address,uint64)", token, amount));
        if (!success) {
            revert("Static mint call failed!");
        }
    }
}
