// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;
pragma experimental ABIEncoderV2;

import "./FeeHelper.sol";

contract NonFungibleTokenCreate is FeeHelper {

    string name;
    string symbol;
    address treasury;

    function createFrozenTokenWithDefaultKeys() external returns (address createdTokenAddress) {
        createdTokenAddress = createFrozenToken(super.getDefaultKeys());
    }

    function createTokenWithDefaultKeys() public returns (address createdTokenAddress) {
        createdTokenAddress = createToken(super.getDefaultKeys());
    }

    function createTokenWithDefaultKeysViaDelegateCall() external returns (address createdTokenAddress) {
        createdTokenAddress = createTokenViaDelegateCall(super.getDefaultKeys());
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
        createdTokenAddress = createTokenWithCustomFees(super.getDefaultKeys(), super.createSingleFixedFeeForToken(amount, tokenId, feeCollector), super.getEmptyRoyaltyFees());
    }

    function createTokenWithCorrectAndWrongTokenFixedFee(uint32 amount, address tokenId, address feeCollector) external returns (address createdTokenAddress) {
        createdTokenAddress = createTokenWithCustomFees(super.getDefaultKeys(), super.createFixedFeesForToken(amount, tokenId, feeCollector, address(0)), super.getEmptyRoyaltyFees());
    }

    function createTokenWithHbarsFixedFee(uint32 amount, address feeCollector) external returns (address createdTokenAddress) {
        createdTokenAddress = createTokenWithCustomFees(super.getDefaultKeys(), super.createSingleFixedFeeForHbars(amount, feeCollector), super.getEmptyRoyaltyFees());
    }

    function createTokenWithCurrentTokenFixedFee(uint32 amount, address feeCollector) external returns (address createdTokenAddress) {
        createdTokenAddress = createTokenWithCustomFees(super.getDefaultKeys(), super.createSingleFixedFeeForCurrentToken(amount, feeCollector), super.getEmptyRoyaltyFees());
    }

    function createTokenWithAllTypesFixedFee(uint32 amount, address tokenId, address feeCollector) external returns (address createdTokenAddress) {
        createdTokenAddress = createTokenWithCustomFees(super.getDefaultKeys(), super.createFixedFeesWithAllTypes(amount, tokenId, feeCollector), super.getEmptyRoyaltyFees());
    }

    function createTokenWithInvalidFlagsFixedFee(uint32 amount, address feeCollector) external returns (address createdTokenAddress) {
        createdTokenAddress = createTokenWithCustomFees(super.getDefaultKeys(), super.createSingleFixedFeeWithInvalidFlags(amount, feeCollector), super.getEmptyRoyaltyFees());
    }

    function createTokenWithFixedFeeForTokenAndHbars(address tokenId, uint32 amount, address feeCollector) external returns (address createdTokenAddress) {
        createdTokenAddress = createTokenWithCustomFees(super.getDefaultKeys(), super.createSingleFixedFeeWithTokenIdAndHbars(amount, tokenId, feeCollector), super.getEmptyRoyaltyFees());
    }

    function createTokenWithRoyaltyFee(uint32 numerator, uint32 denominator, address feeCollector) external returns (address createdTokenAddress) {
        createdTokenAddress = createTokenWithCustomFees(super.getDefaultKeys(), super.getEmptyFixedFees(), super.createSingleRoyaltyFee(numerator, denominator, feeCollector));
    }

    function createTokenWithHbarFixedFeeAndRoaltyFee(uint32 amount, uint32 numerator, uint32 denominator, address fixedFeeCollector, address royaltyFeeCollector) external returns (address createdTokenAddress) {
        createdTokenAddress = createTokenWithCustomFees(super.getDefaultKeys(), super.createSingleFixedFeeForHbars(amount, fixedFeeCollector), super.createSingleRoyaltyFee(numerator, denominator, royaltyFeeCollector));
    }

    function createTokenWithHbarFixedFeeAndRoaltyFeeWithFallbackFee(uint32 fixedFeeAmount, uint32 numerator, uint32 denominator, uint32 fallbackFeeAmount, address tokenId, bool useHbarsForPayment, address fixedFeeCollector, address royaltyFeeCollector) external returns (address createdTokenAddress) {
        createdTokenAddress = createTokenWithCustomFees(super.getDefaultKeys(), super.createSingleFixedFeeForHbars(fixedFeeAmount, fixedFeeCollector), super.createSingleRoyaltyFeeWithFallbackFee(numerator, denominator, fallbackFeeAmount, tokenId, useHbarsForPayment, royaltyFeeCollector));
    }

    function createTokenWithNAmountHbarFixedFeesAndNAmountRoaltyFees(uint8 numberOfFixedFees, uint8 numberOfRoyaltyFees, uint32 amount, uint32 numerator, uint32 denominator,
        address fixedFeeCollector, address royaltyFeeCollector) external returns (address createdTokenAddress) {
        createdTokenAddress = createTokenWithCustomFees(super.getDefaultKeys(),
            super.createNAmountFixedFeesForHbars(numberOfFixedFees, amount, fixedFeeCollector), super.createNAmountRoyaltyFees(numberOfRoyaltyFees, numerator, denominator, royaltyFeeCollector));
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
        HederaTokenService.createNonFungibleToken(token);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }

        createdTokenAddress = tokenAddress;
    }

    function createTokenViaDelegateCall(IHederaTokenService.TokenKey[] memory keys) internal returns (address createdTokenAddress) {
        IHederaTokenService.HederaToken memory token;
        token.name = name;
        token.symbol = symbol;
        token.treasury = treasury;
        token.tokenKeys = keys;

        (bool success, bytes memory result) = precompileAddress.delegatecall(
            abi.encodeWithSelector(IHederaTokenService.createNonFungibleToken.selector, token));
        (int responseCode, address tokenAddress) = success ? abi.decode(result, (int32, address)) : (HederaResponseCodes.UNKNOWN, address(0));

        if (responseCode != HederaResponseCodes.SUCCESS) {
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
        HederaTokenService.createNonFungibleToken(token);

        if (responseCode != HederaResponseCodes.SUCCESS) {
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
        HederaTokenService.createNonFungibleToken(token);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }

        createdTokenAddress = tokenAddress;
    }

    function createTokenWithCustomFees(IHederaTokenService.TokenKey[] memory keys, IHederaTokenService.FixedFee[] memory fixedFees,
        IHederaTokenService.RoyaltyFee[] memory royaltyFees) internal returns (address createdTokenAddress) {
        IHederaTokenService.HederaToken memory token;
        token.name = name;
        token.symbol = symbol;
        token.treasury = treasury;
        token.tokenKeys = keys;

        (int responseCode, address tokenAddress) =
        HederaTokenService.createNonFungibleTokenWithCustomFees(token, fixedFees, royaltyFees);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }

        createdTokenAddress = tokenAddress;
    }
}