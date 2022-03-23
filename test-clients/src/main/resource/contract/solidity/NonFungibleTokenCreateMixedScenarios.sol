// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;
pragma experimental ABIEncoderV2;

import "./NonFungibleTokenCreate.sol";
import "@openzeppelin/contracts/token/ERC721/extensions/IERC721Enumerable.sol";

contract NonFungibleTokenCreateMixedScenarios is NonFungibleTokenCreate {

    constructor(string memory _name, string memory _symbol, address _treasury) {
        name = _name;
        symbol = _symbol;
        treasury = _treasury;
        supplyContract = address(this);
    }

    function mintTokenViaDelegateCall(address token, bytes[] memory metadata) external {
        (bool success, bytes memory result) = precompileAddress.delegatecall(
            abi.encodeWithSelector(IHederaTokenService.mintToken.selector, token, 0, metadata));
        (int responseCode, uint64 newTotalSupply, int64[] memory serialNumbers) =
        success
        ? abi.decode(result, (int32, uint64, int64[]))
        : (HederaResponseCodes.UNKNOWN, 0, new int64[](0));

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ("Nonfungible mint failed");
        }
    }

    function createTokenAndMint(bytes[] memory metadata) external {
        address createdToken = super.createTokenWithDefaultKeys();

        (int response, uint64 newTotalSupply, int64[] memory serialNumbers) = HederaTokenService.mintToken(createdToken, 0, metadata);
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Nonfungible mint failed");
        }

        uint totalSupply = IERC721Enumerable(createdToken).totalSupply();
        if (totalSupply != newTotalSupply) {
            revert ("Total supply is not correct");
        }
    }

    function createTokenAndMintAndTransfer(bytes[] memory metadata, int64[] memory serialNumbersToTransfer, address receiver) external {
        address createdToken = super.createTokenWithDefaultKeys();

        (int response, uint64 newTotalSupply, int64[] memory serialNumbers) = HederaTokenService.mintToken(createdToken, 0, metadata);
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Nonfungible mint failed");
        }

        uint totalSupply = IERC721Enumerable(createdToken).totalSupply();
        if (totalSupply != newTotalSupply) {
            revert ("Total supply is not correct");
        }

        int associateResponse = HederaTokenService.associateToken(receiver, createdToken);
        if (associateResponse != HederaResponseCodes.SUCCESS) {
            revert ("Nonfungible associate failed");
        }

        for(uint i = 0; i < serialNumbersToTransfer.length; i++) {
            int transferResponse = HederaTokenService.transferNFT(supplyContract, receiver, createdToken, serialNumbersToTransfer[i]);

            if(transferResponse != HederaResponseCodes.SUCCESS) {
                revert ("Nonfungible transfer failed");
            }
        }
    }
}