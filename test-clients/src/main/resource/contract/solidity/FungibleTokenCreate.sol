// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;
pragma experimental ABIEncoderV2;

import "./FeeHelper.sol";

contract FungibleTokenCreate is FeeHelper {

    string name;
    string symbol;
    address treasury;

    function createFrozenTokenWithDefaultKeys(uint initialTotalSupply, uint decimals) external returns (address createdTokenAddress) {
        createdTokenAddress = createFrozenToken(initialTotalSupply, decimals, super.getDefaultKeys());
    }

    function createTokenWithDefaultKeys(uint initialTotalSupply, uint decimals) public returns (address createdTokenAddress) {
        createdTokenAddress = createToken(initialTotalSupply, decimals, super.getDefaultKeys());
    }

    function createTokenWithInheritedSupplyKey(uint initialTotalSupply, uint decimals) external returns (address createdTokenAddress) {
        createdTokenAddress = createToken(initialTotalSupply, decimals, super.getCustomSingleTypeKeys(4, 1, ""));
    }

    function createTokenWithAllTypeKeys(uint initialTotalSupply, uint decimals, uint8 keyValueType, bytes memory key) external returns (address createdTokenAddress) {
        createdTokenAddress = createToken(initialTotalSupply, decimals, super.getAllTypeKeys(keyValueType, key));
    }

    function createTokenWithCustomSingleTypeKeys(uint initialTotalSupply, uint decimals, uint8 keyType, uint8 keyValueType, bytes memory key) external returns (address createdTokenAddress) {
        createdTokenAddress = createToken(initialTotalSupply, decimals, super.getCustomSingleTypeKeys(keyType, keyValueType, key));
    }

    function createTokenWithCustomDuplexTypeKeys(uint initialTotalSupply, uint decimals, uint8 firstKeyType, uint8 secondKeyType, uint8 keyValueType, bytes memory key) external returns (address createdTokenAddress) {
        createdTokenAddress = createToken(initialTotalSupply, decimals, super.getCustomDuplexTypeKeys(firstKeyType, secondKeyType, keyValueType, key));
    }

    function createTokenWithTokenFixedFee(uint initialTotalSupply, uint decimals, uint32 amount, address tokenId, address feeCollector) external returns (address createdTokenAddress) {
        createdTokenAddress = createTokenWithCustomFees(initialTotalSupply, decimals, super.getDefaultKeys(), super.createFixedFeesForToken(amount, tokenId, feeCollector), super.getEmptyFractionalFees());
    }

    function createTokenWithCorrectAndWrongTokenFixedFee(uint initialTotalSupply, uint decimals, uint32 amount, address tokenId, address feeCollector) external returns (address createdTokenAddress) {
        createdTokenAddress = createTokenWithCustomFees(initialTotalSupply, decimals, super.getDefaultKeys(), super.createFixedFeesForToken(amount, tokenId, feeCollector, address(0)), super.getEmptyFractionalFees());
    }

    function createTokenWithHbarsFixedFee(uint initialTotalSupply, uint decimals, uint32 amount, address feeCollector) external returns (address createdTokenAddress) {
        createdTokenAddress = createTokenWithCustomFees(initialTotalSupply, decimals, super.getDefaultKeys(), super.createFixedFeesForHbars(amount, feeCollector), super.getEmptyFractionalFees());
    }

    function createTokenWithCurrentTokenFixedFee(uint initialTotalSupply, uint decimals, uint32 amount, address feeCollector) external returns (address createdTokenAddress) {
        createdTokenAddress = createTokenWithCustomFees(initialTotalSupply, decimals, super.getDefaultKeys(), super.createFixedFeesForCurrentToken(amount, feeCollector), super.getEmptyFractionalFees());
    }

    function createTokenWithAllTypesFixedFee(uint initialTotalSupply, uint decimals, uint32 amount, address tokenId, address feeCollector) external returns (address createdTokenAddress) {
        createdTokenAddress = createTokenWithCustomFees(initialTotalSupply, decimals, super.getDefaultKeys(), super.createFixedFeesWithAllTypes(amount, tokenId, feeCollector), super.getEmptyFractionalFees());
    }

    function createTokenWithInvalidFlagsFixedFee(uint initialTotalSupply, uint decimals, uint32 amount, address feeCollector) external returns (address createdTokenAddress) {
        createdTokenAddress = createTokenWithCustomFees(initialTotalSupply, decimals, super.getDefaultKeys(), super.createFixedFeesWithInvalidFlags(amount, feeCollector), super.getEmptyFractionalFees());
    }

    function createTokenWithFixedFeeForTokenAndHbars(uint initialTotalSupply, uint decimals, address tokenId, uint32 amount, address feeCollector) external returns (address createdTokenAddress) {
        createdTokenAddress = createTokenWithCustomFees(initialTotalSupply, decimals, super.getDefaultKeys(), super.createFixedFeesWithTokenIdAndHbars(amount, tokenId, feeCollector), super.getEmptyFractionalFees());
    }

    function createTokenWithFractionalFee(uint initialTotalSupply, uint decimals, uint32 numerator, uint32 denominator,
        bool netOfTransfers, address feeCollector) external returns (address createdTokenAddress) {
        createdTokenAddress = createTokenWithCustomFees(initialTotalSupply, decimals, super.getDefaultKeys(), super.getEmptyFixedFees(), super.createFractionalFees(numerator, denominator, netOfTransfers, feeCollector));
    }

    function createTokenWithFractionalFeeWithLimits(uint initialTotalSupply, uint decimals, uint32 numerator, uint32 denominator, uint32 minimumAmount, uint32 maximumAmount,
        bool netOfTransfers, address feeCollector) external returns (address createdTokenAddress) {
        createdTokenAddress = createTokenWithCustomFees(initialTotalSupply, decimals, super.getDefaultKeys(), super.getEmptyFixedFees(), super.createFractionalFeesWithLimits(numerator, denominator, minimumAmount, maximumAmount, netOfTransfers, feeCollector));
    }

    function createTokenWithHbarFixedFeeAndFractionalFee(uint initialTotalSupply, uint decimals, uint32 amount, uint32 numerator, uint32 denominator,
        bool netOfTransfers, address fixedFeeCollector, address fractionalFeeCollector) external returns (address createdTokenAddress) {
        createdTokenAddress = createTokenWithCustomFees(initialTotalSupply, decimals, super.getDefaultKeys(), super.createFixedFeesForHbars(amount, fixedFeeCollector), super.createFractionalFees(numerator, denominator, netOfTransfers, fractionalFeeCollector));
    }

    function createTokenWithNAmountHbarFixedFeeAndNAmountFractionalFee(uint8 numberOfFixedFees, uint8 numberOfFractionalFees, uint initialTotalSupply, uint decimals,
        uint32 amount, uint32 numerator, uint32 denominator, bool netOfTransfers, address fixedFeeCollector, address fractionalFeeCollector) external returns (address createdTokenAddress) {
        createdTokenAddress = createTokenWithCustomFees(initialTotalSupply, decimals, super.getDefaultKeys(),
            super.createNAmountFixedFeesForHbars(numberOfFixedFees, amount, fixedFeeCollector), super.createNAmountFractionalFees(numberOfFractionalFees, numerator, denominator, netOfTransfers, fractionalFeeCollector));
    }

    function createTokenWithExpiry(uint initialTotalSupply, uint decimals, uint32 second, address autoRenewAccount,
        uint32 autoRenewPeriod, IHederaTokenService.TokenKey[] memory keys) internal returns (address createdTokenAddress) {
        IHederaTokenService.Expiry memory expiry;
        expiry.second = second;
        expiry.autoRenewAccount = autoRenewAccount;
        expiry.autoRenewPeriod = autoRenewPeriod;

        IHederaTokenService.HederaToken memory token;
        token.name = name;
        token.symbol = symbol;
        token.treasury = treasury;
        token.tokenKeys = keys;
        token.expiry = expiry;

        (int responseCode, address tokenAddress) =
        HederaTokenService.createFungibleToken(token, initialTotalSupply, decimals);

        if (responseCode != 22) {
            revert ();
        }

        createdTokenAddress = tokenAddress;
    }

    function createToken(uint initialTotalSupply, uint decimals, IHederaTokenService.TokenKey[] memory keys) internal returns (address createdTokenAddress) {
        IHederaTokenService.HederaToken memory token;
        token.name = name;
        token.symbol = symbol;
        token.treasury = treasury;
        token.tokenKeys = keys;

        (int responseCode, address tokenAddress) =
        HederaTokenService.createFungibleToken(token, initialTotalSupply, decimals);

        if (responseCode != 22) {
            revert ();
        }

        createdTokenAddress = tokenAddress;
    }

    function createFrozenToken(uint initialTotalSupply, uint decimals, IHederaTokenService.TokenKey[] memory keys) internal returns (address createdTokenAddress) {
        IHederaTokenService.HederaToken memory token;
        token.name = name;
        token.symbol = symbol;
        token.treasury = treasury;
        token.tokenKeys = keys;
        token.freezeDefault = true;

        (int responseCode, address tokenAddress) =
        HederaTokenService.createFungibleToken(token, initialTotalSupply, decimals);

        if (responseCode != 22) {
            revert ();
        }

        createdTokenAddress = tokenAddress;
    }

    function createTokenWithCustomFees(uint initialTotalSupply, uint decimals, IHederaTokenService.TokenKey[] memory keys,
        IHederaTokenService.FixedFee[] memory fixedFees, IHederaTokenService.FractionalFee[] memory fractionalFees) internal returns (address createdTokenAddress) {
        IHederaTokenService.HederaToken memory token;
        token.name = name;
        token.symbol = symbol;
        token.treasury = treasury;
        token.tokenKeys = keys;

        (int responseCode, address tokenAddress) =
        HederaTokenService.createFungibleTokenWithCustomFees(token, initialTotalSupply, decimals, fixedFees, fractionalFees);

        if (responseCode != 22) {
            revert ();
        }

        createdTokenAddress = tokenAddress;
    }
}