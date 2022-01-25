// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.6.12;
pragma experimental ABIEncoderV2;

import "./HederaTokenService.sol";
import "./HederaResponseCodes.sol";

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

contract ServiceContract is HederaTokenService {


    function nftTransfer(address token, address sender, address receiver, int64 serialNum) external {
        int response = HederaTokenService.transferNFT(token, sender, receiver, serialNum);

        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Token transfer failed");
        }
    }


    function tokenBurn(address token, uint64 amount, int64[] memory serialNumbers) external {
        (int response, uint64 newTotalSupply) = HederaTokenService.burnToken(token, amount, serialNumbers);

        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Token burn failed");
        }

   }

    function tokenMint(address token, uint64 amount) external {
        (int responseCode, uint64 newTotalSupply, int[] memory serialNumbers) = HederaTokenService.mintToken(token, amount, new bytes[](0));

        if (responseCode != HederaResponseCodes.SUCCESS) {
                revert ("Mint of fungible token failed!");
            }
   }

}