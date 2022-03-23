// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;
pragma experimental ABIEncoderV2;

import "./FeeHelper.sol";

contract FungibleTokenCreate is FeeHelper {

    string name;
    string symbol;
    address treasury;
    uint initialTotalSupply;
    uint decimals;

    function createFrozenTokenWithDefaultKeys() external returns (address createdTokenAddress) {
        createdTokenAddress = createFrozenToken(super.getDefaultKeys());
    }

    function createTokenWithDefaultKeys() public returns (address createdTokenAddress) {
        createdTokenAddress = createToken(super.getDefaultKeys());
    }

    function createTokenWithInheritedSupplyKey() external returns (address createdTokenAddress) {
        createdTokenAddress = createToken(super.getCustomSingleTypeKeys(4, 1, ""));
    }

    function createTokenWithAllTypeKeys(uint8 keyValueType, bytes memory key) external returns (address createdTokenAddress) {
        createdTokenAddress = createToken(super.getAllTypeKeys(keyValueType, key));
    }

    function createTokenWithCustomSingleTypeKeys(uint8 keyType, uint8 keyValueType, bytes memory key) external returns (address createdTokenAddress) {
        createdTokenAddress = createToken(super.getCustomSingleTypeKeys(keyType, keyValueType, key));
    }

    function createTokenWithCustomDuplexTypeKeys(uint8 firstKeyType, uint8 secondKeyType, uint8 keyValueType, bytes memory key) external returns (address createdTokenAddress) {
        createdTokenAddress = createToken(super.getCustomDuplexTypeKeys(firstKeyType, secondKeyType, keyValueType, key));
    }

    function createTokenWithTokenFixedFee(uint32 amount, address tokenId, address feeCollector) external returns (address createdTokenAddress) {
        createdTokenAddress = createTokenWithCustomFees(super.getDefaultKeys(), super.createFixedFeesForToken(amount, tokenId, feeCollector), super.getEmptyFractionalFees());
    }

    function createTokenWithCorrectAndWrongTokenFixedFee(uint32 amount, address tokenId, address feeCollector) external returns (address createdTokenAddress) {
        createdTokenAddress = createTokenWithCustomFees(super.getDefaultKeys(), super.createFixedFeesForToken(amount, tokenId, feeCollector, address(0)), super.getEmptyFractionalFees());
    }

    function createTokenWithHbarsFixedFee(uint32 amount, address feeCollector) external returns (address createdTokenAddress) {
        createdTokenAddress = createTokenWithCustomFees(super.getDefaultKeys(), super.createFixedFeesForHbars(amount, feeCollector), super.getEmptyFractionalFees());
    }

    function createTokenWithCurrentTokenFixedFee(uint32 amount, address feeCollector) external returns (address createdTokenAddress) {
        createdTokenAddress = createTokenWithCustomFees(super.getDefaultKeys(), super.createFixedFeesForCurrentToken(amount, feeCollector), super.getEmptyFractionalFees());
    }

    function createTokenWithAllTypesFixedFee(uint32 amount, address tokenId, address feeCollector) external returns (address createdTokenAddress) {
        createdTokenAddress = createTokenWithCustomFees(super.getDefaultKeys(), super.createFixedFeesWithAllTypes(amount, tokenId, feeCollector), super.getEmptyFractionalFees());
    }

    function createTokenWithInvalidFlagsFixedFee(uint32 amount, address feeCollector) external returns (address createdTokenAddress) {
        createdTokenAddress = createTokenWithCustomFees(super.getDefaultKeys(), super.createFixedFeesWithInvalidFlags(amount, feeCollector), super.getEmptyFractionalFees());
    }

    function createTokenWithFixedFeeForTokenAndHbars(address tokenId, uint32 amount, address feeCollector) external returns (address createdTokenAddress) {
        createdTokenAddress = createTokenWithCustomFees(super.getDefaultKeys(), super.createFixedFeesWithTokenIdAndHbars(amount, tokenId, feeCollector), super.getEmptyFractionalFees());
    }

    function createTokenWithFractionalFee(uint32 numerator, uint32 denominator, bool netOfTransfers, address feeCollector) external returns (address createdTokenAddress) {
        createdTokenAddress = createTokenWithCustomFees(super.getDefaultKeys(), super.getEmptyFixedFees(), super.createFractionalFees(numerator, denominator, netOfTransfers, feeCollector));
    }

    function createTokenWithFractionalFeeWithLimits(uint32 numerator, uint32 denominator, uint32 minimumAmount, uint32 maximumAmount,
        bool netOfTransfers, address feeCollector) external returns (address createdTokenAddress) {
        createdTokenAddress = createTokenWithCustomFees(super.getDefaultKeys(), super.getEmptyFixedFees(), super.createFractionalFeesWithLimits(numerator, denominator, minimumAmount, maximumAmount, netOfTransfers, feeCollector));
    }

    function createTokenWithHbarFixedFeeAndFractionalFee(uint32 amount, uint32 numerator, uint32 denominator,
        bool netOfTransfers, address fixedFeeCollector, address fractionalFeeCollector) external returns (address createdTokenAddress) {
        createdTokenAddress = createTokenWithCustomFees(super.getDefaultKeys(), super.createFixedFeesForHbars(amount, fixedFeeCollector), super.createFractionalFees(numerator, denominator, netOfTransfers, fractionalFeeCollector));
    }

    function createTokenWithNAmountHbarFixedFeeAndNAmountFractionalFee(uint8 numberOfFixedFees, uint8 numberOfFractionalFees, uint32 amount, uint32 numerator, uint32 denominator, bool netOfTransfers,
        address fixedFeeCollector, address fractionalFeeCollector) external returns (address createdTokenAddress) {
        createdTokenAddress = createTokenWithCustomFees(super.getDefaultKeys(),
            super.createNAmountFixedFeesForHbars(numberOfFixedFees, amount, fixedFeeCollector), super.createNAmountFractionalFees(numberOfFractionalFees, numerator, denominator, netOfTransfers, fractionalFeeCollector));
    }

    function createTokenWithExpiry(uint32 second, address autoRenewAccount,
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

    function createToken(IHederaTokenService.TokenKey[] memory keys) internal returns (address createdTokenAddress) {
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

    function createFrozenToken(IHederaTokenService.TokenKey[] memory keys) internal returns (address createdTokenAddress) {
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

    function createTokenWithCustomFees(IHederaTokenService.TokenKey[] memory keys,
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