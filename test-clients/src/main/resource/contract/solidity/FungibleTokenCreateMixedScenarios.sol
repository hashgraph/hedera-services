// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;
pragma experimental ABIEncoderV2;

import "./TokenCreateContract.sol";
import "@openzeppelin/contracts/token/ERC20/IERC20.sol";

contract FungibleTokenCreateMixedScenarios is FungibleTokenCreate {

    constructor(string memory _name, string memory _symbol, address _treasury, uint _initialTotalSupply, uint _decimals) {
        name = _name;
        symbol = _symbol;
        treasury = _treasury;
        initialTotalSupply = _initialTotalSupply;
        decimals = _decimals;
        supplyContract = address(this);
    }

    function mintTokenViaDelegateCall(address token, uint64 amount) external {
        (bool success, bytes memory result) = precompileAddress.delegatecall(
            abi.encodeWithSelector(IHederaTokenService.mintToken.selector, token, amount, new bytes[](0)));
        (int responseCode, uint64 newTotalSupply, int64[] memory serialNumbers) =
        success
        ? abi.decode(result, (int32, uint64, int64[]))
        : (HederaResponseCodes.UNKNOWN, 0, new int64[](0));

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ("Fungible mint failed");
        }
    }

    function createTokenAndMint(uint64 amountToMint) external {
        address createdToken = super.createTokenWithDefaultKeys();

        (int response, uint64 newTotalSupply, int64[] memory serialNumbers) = HederaTokenService.mintToken(createdToken, amountToMint, new bytes[](0));
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Fungible mint failed");
        }

        uint totalSupply = IERC20(createdToken).totalSupply();
        if (totalSupply != newTotalSupply) {
            revert ("Total supply is not correct");
        }
    }

    function createTokenAndMintAndTransfer(uint64 amountToMint, address receiver) external {
        address createdToken = super.createTokenWithDefaultKeys();

        (int mintResponse, uint64 newTotalSupply, int64[] memory serialNumbers) = HederaTokenService.mintToken(createdToken, amountToMint, new bytes[](0));
        if (mintResponse != HederaResponseCodes.SUCCESS) {
            revert ("Fungible mint failed");
        }

        uint totalSupply = IERC20(createdToken).totalSupply();
        if (totalSupply != newTotalSupply) {
            revert ("Total supply is not correct");
        }

        int associateResponse = HederaTokenService.associateToken(receiver, createdToken);
        if (associateResponse != HederaResponseCodes.SUCCESS) {
            revert ("Fungible associate failed");
        }

        bool transferResponse = IERC20(createdToken).transfer(receiver, 5);

        if(!transferResponse) {
            revert ("Fungible transfer failed");
        }
    }
}