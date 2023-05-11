// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.12;
pragma experimental ABIEncoderV2;

import "./IHederaTokenService.sol";
import "./HederaResponseCodes.sol";

contract MixedFramesScenarios {

    address constant precompileAddress = address(0x167);
    address mixedMintTokenContractAddress;

    constructor(address _mixedMintTokenContractAddress) public {
        mixedMintTokenContractAddress = _mixedMintTokenContractAddress;
    }

   function burnCallAfterNestedMintCallWithPrecompileCall(int64 amount, address tokenAddress) public {
        (bool mintSuccess, bytes memory mintResult) = mixedMintTokenContractAddress.call(abi.encodeWithSelector
        (MixedMintTokenContract.mintTokenCall.selector, amount, tokenAddress));

        (int mintResponse, int64 newTotalSupply, int[] memory serialNumbers) =
            mintSuccess
                ? abi.decode(mintResult, (int32, int64, int[]))
                : (HederaResponseCodes.UNKNOWN, int64(0), new int[](0));

        if (mintResponse != HederaResponseCodes.SUCCESS) {
            revert ("Token mint failed");
        }

        (bool burnSuccess, bytes memory burnResult) = precompileAddress.call(
            abi.encodeWithSelector(IHederaTokenService.burnToken.selector,
            tokenAddress, amount, new int64[](0)));
        (int burnResponse, int64 newTotalSupplyAfterBurn) =
            burnSuccess
                ? abi.decode(burnResult, (int32, int64))
                : (HederaResponseCodes.UNKNOWN, int64(0));

        if (burnResponse != HederaResponseCodes.SUCCESS) {
            revert ("Token burn failed");
        }
   }

   function burnDelegateCallAfterNestedMintCallWithPrecompileCall(int64 amount, address tokenAddress) public {
        (bool mintSuccess, bytes memory mintResult) = mixedMintTokenContractAddress.call(abi.encodeWithSelector
        (MixedMintTokenContract.mintTokenCall.selector, amount, tokenAddress));

        (int mintResponse, int64 newTotalSupply, int[] memory serialNumbers) =
            mintSuccess
                ? abi.decode(mintResult, (int32, int64, int[]))
                : (HederaResponseCodes.UNKNOWN, int64(0), new int[](0));

        if (mintResponse != HederaResponseCodes.SUCCESS) {
            revert ("Token mint failed");
        }

        (bool burnSuccess, bytes memory burnResult) = precompileAddress.delegatecall(
            abi.encodeWithSelector(IHederaTokenService.burnToken.selector,
            tokenAddress, amount, new int64[](0)));
        (int burnResponse, int64 newTotalSupplyAfterBurn) =
            burnSuccess
                ? abi.decode(burnResult, (int32, int64))
                : (HederaResponseCodes.UNKNOWN, int64(0));

        if (burnResponse != HederaResponseCodes.SUCCESS) {
            revert ("Token burn failed");
        }
   }

   function burnDelegateCallAfterNestedMintDelegateCallWithPrecompileCall(int64 amount, address tokenAddress) public {
        (bool mintSuccess, bytes memory mintResult) = mixedMintTokenContractAddress.delegatecall(abi.encodeWithSelector
        (MixedMintTokenContract.mintTokenCall.selector, amount, tokenAddress));

        (int mintResponse, int64 newTotalSupply, int[] memory serialNumbers) =
            mintSuccess
                ? abi.decode(mintResult, (int32, int64, int[]))
                : (HederaResponseCodes.UNKNOWN, int64(0), new int[](0));

        if (mintResponse != HederaResponseCodes.SUCCESS) {
            revert ("Token mint failed");
        }

        (bool burnSuccess, bytes memory burnResult) = precompileAddress.delegatecall(
            abi.encodeWithSelector(IHederaTokenService.burnToken.selector,
            tokenAddress, amount, new int64[](0)));
        (int burnResponse, int64 newTotalSupplyAfterBurn) =
            burnSuccess
                ? abi.decode(burnResult, (int32, int64))
                : (HederaResponseCodes.UNKNOWN, int64(0));

        if (burnResponse != HederaResponseCodes.SUCCESS) {
            revert ("Token burn failed");
        }
   }

   function burnCallAfterNestedMintDelegateCallWithPrecompileCall(int64 amount, address tokenAddress) public {
        (bool mintSuccess, bytes memory mintResult) = mixedMintTokenContractAddress.delegatecall(abi.encodeWithSelector
        (MixedMintTokenContract.mintTokenCall.selector, amount, tokenAddress));

        (int mintResponse, int64 newTotalSupply, int[] memory serialNumbers) =
            mintSuccess
                ? abi.decode(mintResult, (int32, int64, int[]))
                : (HederaResponseCodes.UNKNOWN, int64(0), new int[](0));

        if (mintResponse != HederaResponseCodes.SUCCESS) {
            revert ("Token mint failed");
        }

        (bool burnSuccess, bytes memory burnResult) = precompileAddress.call(
            abi.encodeWithSelector(IHederaTokenService.burnToken.selector,
            tokenAddress, amount, new int64[](0)));
        (int burnResponse, int64 newTotalSupplyAfterBurn) =
            burnSuccess
                ? abi.decode(burnResult, (int32, int64))
                : (HederaResponseCodes.UNKNOWN, int64(0));

        if (burnResponse != HederaResponseCodes.SUCCESS) {
            revert ("Token burn failed");
        }
   }

   function burnCallAfterNestedMintCallWithPrecompileDelegateCall(int64 amount, address tokenAddress) public {
        (bool mintSuccess, bytes memory mintResult) = mixedMintTokenContractAddress.call(abi.encodeWithSelector
        (MixedMintTokenContract.mintTokenDelegateCall.selector, amount, tokenAddress));

        (int mintResponse, int64 newTotalSupply, int[] memory serialNumbers) =
            mintSuccess
                ? abi.decode(mintResult, (int32, int64, int[]))
                : (HederaResponseCodes.UNKNOWN, int64(0), new int[](0));

        if (mintResponse != HederaResponseCodes.SUCCESS) {
            revert ("Token mint failed");
        }

        (bool burnSuccess, bytes memory burnResult) = precompileAddress.call(
            abi.encodeWithSelector(IHederaTokenService.burnToken.selector,
            tokenAddress, amount, new int64[](0)));
        (int burnResponse, int64 newTotalSupplyAfterBurn) =
            burnSuccess
                ? abi.decode(burnResult, (int32, int64))
                : (HederaResponseCodes.UNKNOWN, int64(0));

        if (burnResponse != HederaResponseCodes.SUCCESS) {
            revert ("Token burn failed");
        }
   }

   function burnDelegateCallAfterNestedMintCallWithPrecompileDelegateCall(int64 amount, address tokenAddress) public {
        (bool mintSuccess, bytes memory mintResult) = mixedMintTokenContractAddress.call(abi.encodeWithSelector
        (MixedMintTokenContract.mintTokenDelegateCall.selector, amount, tokenAddress));

        (int mintResponse, int64 newTotalSupply, int[] memory serialNumbers) =
            mintSuccess
                ? abi.decode(mintResult, (int32, int64, int[]))
                : (HederaResponseCodes.UNKNOWN, int64(0), new int[](0));

        if (mintResponse != HederaResponseCodes.SUCCESS) {
            revert ("Token mint failed");
        }

        (bool burnSuccess, bytes memory burnResult) = precompileAddress.delegatecall(
            abi.encodeWithSelector(IHederaTokenService.burnToken.selector,
            tokenAddress, amount, new int64[](0)));
        (int burnResponse, int64 newTotalSupplyAfterBurn) =
            burnSuccess
                ? abi.decode(burnResult, (int32, int64))
                : (HederaResponseCodes.UNKNOWN, int64(0));

        if (burnResponse != HederaResponseCodes.SUCCESS) {
            revert ("Token burn failed");
        }
   }

   function burnDelegateCallAfterNestedMintDelegateCallWithPrecompileDelegateCall(int64 amount, address tokenAddress) public {
        (bool mintSuccess, bytes memory mintResult) = mixedMintTokenContractAddress.delegatecall(abi.encodeWithSelector
        (MixedMintTokenContract.mintTokenDelegateCall.selector, amount, tokenAddress));

        (int mintResponse, int64 newTotalSupply, int[] memory serialNumbers) =
            mintSuccess
                ? abi.decode(mintResult, (int32, int64, int[]))
                : (HederaResponseCodes.UNKNOWN, int64(0), new int[](0));

        if (mintResponse != HederaResponseCodes.SUCCESS) {
            revert ("Token mint failed");
        }

        (bool burnSuccess, bytes memory burnResult) = precompileAddress.delegatecall(
            abi.encodeWithSelector(IHederaTokenService.burnToken.selector,
            tokenAddress, amount, new int64[](0)));
        (int burnResponse, int64 newTotalSupplyAfterBurn) =
            burnSuccess
                ? abi.decode(burnResult, (int32, int64))
                : (HederaResponseCodes.UNKNOWN, int64(0));

        if (burnResponse != HederaResponseCodes.SUCCESS) {
            revert ("Token burn failed");
        }
   }

   function burnCallAfterNestedMintDelegateCallWithPrecompileDelegateCall(int64 amount, address tokenAddress) public {
        (bool mintSuccess, bytes memory mintResult) = mixedMintTokenContractAddress.delegatecall(abi.encodeWithSelector
        (MixedMintTokenContract.mintTokenDelegateCall.selector, amount, tokenAddress));

        (int mintResponse, int64 newTotalSupply, int[] memory serialNumbers) =
            mintSuccess
                ? abi.decode(mintResult, (int32, int64, int[]))
                : (HederaResponseCodes.UNKNOWN, int64(0), new int[](0));

        if (mintResponse != HederaResponseCodes.SUCCESS) {
            revert ("Token mint failed");
        }

        (bool burnSuccess, bytes memory burnResult) = precompileAddress.call(
            abi.encodeWithSelector(IHederaTokenService.burnToken.selector,
            tokenAddress, amount, new int64[](0)));
        (int burnResponse, int64 newTotalSupplyAfterBurn) =
            burnSuccess
                ? abi.decode(burnResult, (int32, int64))
                : (HederaResponseCodes.UNKNOWN, int64(0));

        if (burnResponse != HederaResponseCodes.SUCCESS) {
            revert ("Token burn failed");
        }
   }
}

contract MixedMintTokenContract {

    address constant precompileAddress = address(0x167);

    function mintTokenCall(int64 amount, address tokenAddress) public
        returns (bool success, bytes memory result) {
    (success, result) = precompileAddress.call(
            abi.encodeWithSelector(IHederaTokenService.mintToken.selector,
            tokenAddress, amount, new bytes[](0)));
    }

    function mintTokenDelegateCall(int64 amount, address tokenAddress) public
        returns (bool success, bytes memory result) {
    (success, result) = precompileAddress.delegatecall(
            abi.encodeWithSelector(IHederaTokenService.mintToken.selector,
            tokenAddress, amount, new bytes[](0)));
    }
}