// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;
pragma experimental ABIEncoderV2;

import "./HederaTokenService.sol";
import "./HederaResponseCodes.sol";
import "./IHederaTokenService.sol";

contract FeeHelper {

    function createFixedFeesForToken(uint32 amount, address tokenId, address feeCollector) external pure returns (IHederaTokenService.FixedFee[] memory fixedFees) {
        IHederaTokenService.FixedFee memory fixedFee = createFixedFeeForToken(amount, tokenId, feeCollector);
        fixedFees[0] = fixedFee;
    }

    function createFixedFeesForHbars(uint32 amount, address feeCollector) external pure returns (IHederaTokenService.FixedFee[] memory fixedFees) {
        IHederaTokenService.FixedFee memory fixedFee = createFixedFeeForHbars(amount, feeCollector);
        fixedFees[0] = fixedFee;
    }

    function createFixedFeesForCurrentToken(uint32 amount, address feeCollector) external pure returns (IHederaTokenService.FixedFee[] memory fixedFees) {
        IHederaTokenService.FixedFee memory fixedFee = createFixedFeeForCurrentToken(amount, feeCollector);
        fixedFees[0] = fixedFee;
    }

    function createFixedFeesWithAllTypes(uint32 amount, address tokenId, address feeCollector) external pure returns (IHederaTokenService.FixedFee[] memory fixedFees) {
        IHederaTokenService.FixedFee memory fixedFeeForToken = createFixedFeeForToken(amount, tokenId, feeCollector);
        IHederaTokenService.FixedFee memory fixedFeeForHbars = createFixedFeeForHbars(amount, feeCollector);
        IHederaTokenService.FixedFee memory fixedFeeForCurrentToken = createFixedFeeForCurrentToken(amount, feeCollector);
        fixedFees[1] = fixedFeeForToken;
        fixedFees[1] = fixedFeeForHbars;
        fixedFees[2] = fixedFeeForCurrentToken;
    }

    function createFixedFeeForToken(uint32 amount, address tokenId, address feeCollector) internal pure returns (IHederaTokenService.FixedFee memory fixedFee) {
        fixedFee.amount = amount;
        fixedFee.tokenId = tokenId;
        fixedFee.feeCollector = feeCollector;
    }

    function createFixedFeeForHbars(uint32 amount, address feeCollector) internal pure returns (IHederaTokenService.FixedFee memory fixedFee) {
        fixedFee.amount = amount;
        fixedFee.useHbarsForPayment = true;
        fixedFee.feeCollector = feeCollector;
    }

    function createFixedFeeForCurrentToken(uint32 amount, address feeCollector) internal pure returns (IHederaTokenService.FixedFee memory fixedFee) {
        fixedFee.amount = amount;
        fixedFee.useCurrentTokenForPayment = true;
        fixedFee.feeCollector = feeCollector;
    }

    //Used for negative scenarios
    function createFixedFeeWithInvalidFlags(uint32 amount, address feeCollector) internal pure returns (IHederaTokenService.FixedFee memory fixedFee) {
        fixedFee.amount = amount;
        fixedFee.useHbarsForPayment = true;
        fixedFee.useCurrentTokenForPayment = true;
        fixedFee.feeCollector = feeCollector;
    }

    //Used for negative scenarios
    function createFixedFeeWithTokenIdAndHbars(uint32 amount, address tokenId, address feeCollector) internal pure returns (IHederaTokenService.FixedFee memory fixedFee) {
        fixedFee.amount = amount;
        fixedFee.tokenId = tokenId;
        fixedFee.useHbarsForPayment = true;
        fixedFee.feeCollector = feeCollector;
    }

    function createFractionalFeeWithoutLimits(uint32 numerator, uint32 denominator,
        bool netOfTransfers,  address feeCollector) internal pure returns (IHederaTokenService.FractionalFee memory fractionalFee) {
        fractionalFee.numerator = numerator;
        fractionalFee.denominator = denominator;
        fractionalFee.netOfTransfers = netOfTransfers;
        fractionalFee.feeCollector = feeCollector;
    }

    function createFractionalFee(uint32 numerator, uint32 denominator, uint32 minimumAmount, uint32 maximumAmount,
        bool netOfTransfers,  address feeCollector) internal pure returns (IHederaTokenService.FractionalFee memory fractionalFee) {
        fractionalFee.numerator = numerator;
        fractionalFee.denominator = denominator;
        fractionalFee.minimumAmount = minimumAmount;
        fractionalFee.maximumAmount = maximumAmount;
        fractionalFee.netOfTransfers = netOfTransfers;
        fractionalFee.feeCollector = feeCollector;
    }

    function createRoyaltylFee(uint32 numerator, uint32 denominator, IHederaTokenService.FixedFee memory fixedFee,
        address feeCollector) internal pure returns (IHederaTokenService.RoyaltyFee memory royaltyFee) {
        royaltyFee.numerator = numerator;
        royaltyFee.denominator = denominator;
        royaltyFee.fixedFee = fixedFee;
        royaltyFee.feeCollector = feeCollector;
    }
}