// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.6.0 <0.9.0;

import "./hip-206/HederaTokenService.sol";
import "./hip-206/IHederaTokenService.sol";
import "./hip-206/HederaResponseCodes.sol";

contract VersatileTransfers is HederaTokenService {
    FeeDistributor feeDistributor;

    constructor(address feeDistributorContractAddress) public {
        feeDistributor = FeeDistributor(feeDistributorContractAddress);
    }

    function distributeTokens(address tokenAddress, address[] calldata accounts, int64[] calldata amounts) public {
        int response = HederaTokenService.transferTokens(tokenAddress, accounts, amounts);
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Transfer of tokens failed");
        }
    }

    function transferNft(address token, address sender, address receiver, int64 serialNum) external {
        int response = HederaTokenService.transferNFT(token, sender, receiver, serialNum);
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Transfer of NFT failed");
        }
    }

    function transferNfts(address token, address[] memory sender, address[] memory receiver, int64[] memory serialNumber) external {
        int response = HederaTokenService.transferNFTs(token, sender, receiver, serialNumber);
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Transfer of NFTs failed");
        }
    }

    function feeDistributionAfterTransfer(address tokenAddress, address feeTokenAddress, address[] calldata accounts, int64[] calldata amounts, address feeCollector) external {
        int response = HederaTokenService.transferTokens(tokenAddress, accounts, amounts);
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Transfer of tokens failed");
        }

        feeDistributor.distributeFees(feeTokenAddress, feeCollector, accounts[0]);
    }

    function feeDistributionAfterTransferStaticNestedCall(address tokenAddress, address feeTokenAddress, address[] calldata accounts, int64[] calldata amounts, address feeCollector) external {
        int response = HederaTokenService.transferTokens(tokenAddress, accounts, amounts);
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Transfer of tokens failed");
        }

        feeDistributor.distributeFeesStaticCall(feeTokenAddress, feeCollector, accounts[0]);
    }
}

contract FeeDistributor is HederaTokenService {
    function distributeFees(address tokenAddress, address feeCollector, address receiver) external {
        int response = HederaTokenService.transferToken(tokenAddress, feeCollector, receiver, 100);
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Transfer of tokens failed");
        }
    }

    function distributeFeesStaticCall(address tokenAddress, address feeCollector, address receiver) external {
        (bool success, bytes memory result) = precompileAddress.call(
            abi.encodeWithSelector(IHederaTokenService.transferToken.selector,
            tokenAddress, feeCollector, receiver, 100));
        int responseCode = success ? abi.decode(result, (int32)) : HederaResponseCodes.UNKNOWN;
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ("Transfer of tokens failed");
        }
    }
}