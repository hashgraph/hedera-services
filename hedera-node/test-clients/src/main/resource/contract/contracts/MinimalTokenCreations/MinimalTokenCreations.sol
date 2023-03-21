// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;
pragma experimental ABIEncoderV2;

import "./KeyHelper.sol";
import "./IHederaTokenService.sol";
import "./HederaResponseCodes.sol";

contract MinimalTokenCreations is KeyHelper {
    IHederaTokenService HTS = IHederaTokenService(address(0x167));

    int32 decimals = 1;
    int64 initialTotalSupply = 10000;
    string memo = "MEMO";

    function updateTokenWithNewTreasury(
        address tokenAddress,
        address newTreasury
    ) public payable {
        IHederaTokenService.HederaToken memory token;
        token.treasury = newTreasury;

        int rc = HTS.updateTokenInfo(tokenAddress, token);

        require(rc == HederaResponseCodes.SUCCESS);
    }

    function updateTokenWithNewAutoRenewInfo(
        address tokenAddress,
        address newAutoRenewAccount,
        int64 newAutoRenewPeriod
    ) public payable {
        IHederaTokenService.Expiry memory expiry;
        expiry.autoRenewAccount = newAutoRenewAccount;
        expiry.autoRenewPeriod = newAutoRenewPeriod;

        int rc = HTS.updateTokenExpiryInfo(tokenAddress, expiry);

        require(rc == HederaResponseCodes.SUCCESS);
    }

    function makeRenewableToken(
        address autoRenewAccount,
        int64 autoRenewPeriod
    ) public payable returns (address tokenAddress) {
        IHederaTokenService.HederaToken memory token = buildDefaultStructFrom(autoRenewAccount, autoRenewPeriod);

        (int64 rc, address createdAddress) = HTS.createFungibleToken{value : msg.value}(token, initialTotalSupply, decimals);
        require(rc == HederaResponseCodes.SUCCESS);

        tokenAddress = createdAddress;
    }

    function makeRenewableTokenIndirectly(
        address autoRenewAccount,
        int64 autoRenewPeriod
    ) public payable returns (address tokenAddress) {
        CreationHelper proxy = new CreationHelper();
        return proxy.makeRenewableToken{value : msg.value}(autoRenewAccount, autoRenewPeriod);
    }

    function makeRenewableTokenWithFractionalFee(
        address autoRenewAccount,
        int64 autoRenewPeriod,
        address feeCollector
    ) public payable returns (address tokenAddress) {
        IHederaTokenService.HederaToken memory token = buildDefaultStructFrom(autoRenewAccount, autoRenewPeriod);
        IHederaTokenService.FractionalFee[] memory fractionalFees = new IHederaTokenService.FractionalFee[](1);
        fractionalFees[0] = createFractionalFee(1, 10, false, feeCollector);
        IHederaTokenService.FixedFee[] memory fixedFees = new IHederaTokenService.FixedFee[](0);

        (int rc, address createdAddress) =
        HTS.createFungibleTokenWithCustomFees{value : msg.value}(
            token, initialTotalSupply, decimals, fixedFees, fractionalFees);
        require(rc == HederaResponseCodes.SUCCESS);

        tokenAddress = createdAddress;
    }

    function createFractionalFee(
        int64 numerator,
        int64 denominator,
        bool netOfTransfers,
        address feeCollector
    )
    internal
    pure
    returns (IHederaTokenService.FractionalFee memory fractionalFee)
    {
        fractionalFee.numerator = numerator;
        fractionalFee.denominator = denominator;
        fractionalFee.netOfTransfers = netOfTransfers;
        fractionalFee.feeCollector = feeCollector;
    }

    function makeRenewableTokenWithSelfDenominatedFixedFee(
        address autoRenewAccount,
        int64 autoRenewPeriod,
        address feeCollector
    ) public payable returns (address tokenAddress) {
        IHederaTokenService.HederaToken memory token = buildDefaultStructFrom(autoRenewAccount, autoRenewPeriod);
        IHederaTokenService.FractionalFee[] memory fractionalFees = new IHederaTokenService.FractionalFee[](0);
        IHederaTokenService.FixedFee[] memory fixedFees = new IHederaTokenService.FixedFee[](1);
        fixedFees[0] = createFixedSelfDenominatedFee(10, feeCollector);

        (int rc, address createdAddress) =
        HTS.createFungibleTokenWithCustomFees{value : msg.value}(token, initialTotalSupply, decimals, fixedFees, fractionalFees);

        require(rc == HederaResponseCodes.SUCCESS);

        tokenAddress = createdAddress;
    }

    function createFixedSelfDenominatedFee(int64 amount, address feeCollector)
    internal
    pure
    returns (IHederaTokenService.FixedFee memory fixedFee)
    {
        fixedFee.amount = amount;
        fixedFee.useCurrentTokenForPayment = true;
        fixedFee.feeCollector = feeCollector;
    }


    function buildDefaultStructFrom(
        address autoRenewAccount,
        int64 autoRenewPeriod
    ) internal returns (IHederaTokenService.HederaToken memory token) {
        IHederaTokenService.TokenKey[] memory keys = new IHederaTokenService.TokenKey[](0);

        IHederaTokenService.Expiry memory expiry;
        expiry.autoRenewAccount = autoRenewAccount;
        expiry.autoRenewPeriod = autoRenewPeriod;

        token.name = "NAME";
        token.symbol = "SYMBOL";
        token.treasury = address(this);
        token.tokenKeys = keys;
        token.expiry = expiry;
        token.memo = "MEMO";
    }
}

contract CreationHelper is HederaTokenService {
    IHederaTokenService HTS = IHederaTokenService(address(0x167));

    function makeRenewableToken(
        address autoRenewAccount,
        int64 autoRenewPeriod
    ) public payable returns (address tokenAddress) {
        IHederaTokenService.HederaToken memory token;
        IHederaTokenService.TokenKey[] memory keys = new IHederaTokenService.TokenKey[](0);

        IHederaTokenService.Expiry memory expiry;
        expiry.autoRenewAccount = autoRenewAccount;
        expiry.autoRenewPeriod = autoRenewPeriod;

        token.name = "NAME";
        token.symbol = "SYMBOL";
        token.treasury = address(this);
        token.tokenKeys = keys;
        token.expiry = expiry;
        token.memo = "MEMO";

        (int rc, address createdAddress) = HTS.createFungibleToken{value : msg.value}(token, int64(10000), int32(1));
        require(rc == HederaResponseCodes.SUCCESS);

        tokenAddress = createdAddress;
    }
}
