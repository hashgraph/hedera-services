// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;
pragma experimental ABIEncoderV2;

import "./hip-206/KeyHelper.sol";
import "./hip-206/HederaTokenService.sol";

contract MinimalTokenCreations is KeyHelper {
    uint32 decimals = 1;
    uint64 initialTotalSupply = 10000;
    string memo = "MEMO";

    function updateTokenWithNewTreasury(
        address tokenAddress,
        address newTreasury
    ) public payable {
        IHederaTokenService.HederaToken memory token;
        token.treasury = newTreasury;

        int rc = super.updateTokenInfo(tokenAddress, token);

        require(rc == 22);
    }

    function updateTokenWithNewAutoRenewInfo(
        address tokenAddress,
        address newAutoRenewAccount,
        uint32 newAutoRenewPeriod
    ) public payable {
        IHederaTokenService.Expiry memory expiry;
        expiry.autoRenewAccount = newAutoRenewAccount;
        expiry.autoRenewPeriod = newAutoRenewPeriod;

        int rc = super.updateTokenExpiryInfo(tokenAddress, expiry);

        require(rc == 22);
    }

    function makeRenewableToken(
        address autoRenewAccount,
        uint32 autoRenewPeriod
    ) public payable returns (address tokenAddress) {
        IHederaTokenService.HederaToken memory token = buildDefaultStructFrom(autoRenewAccount, autoRenewPeriod);

        (int rc, address createdAddress) =
            HederaTokenService.createFungibleToken(token, initialTotalSupply, decimals);
        require(rc == 22);

        tokenAddress = createdAddress;
    }

    function makeRenewableTokenIndirectly(
        address autoRenewAccount,
        uint32 autoRenewPeriod
    ) public payable returns (address tokenAddress) {
        CreationHelper proxy = new CreationHelper();
        return proxy.makeRenewableToken{value: msg.value}(autoRenewAccount, autoRenewPeriod);
    }

    function makeRenewableTokenWithFractionalFee(
        address autoRenewAccount,
        uint32 autoRenewPeriod,
        address feeCollector
    ) public payable returns (address tokenAddress) {
        IHederaTokenService.HederaToken memory token = buildDefaultStructFrom(autoRenewAccount, autoRenewPeriod);
        IHederaTokenService.FractionalFee[] memory fractionalFees = new IHederaTokenService.FractionalFee[](1);
        fractionalFees[0] = createFractionalFee(1, 10, false, feeCollector);
        IHederaTokenService.FixedFee[] memory fixedFees = new IHederaTokenService.FixedFee[](0);

        (int rc, address createdAddress) =
            HederaTokenService.createFungibleTokenWithCustomFees(
              token, initialTotalSupply, decimals, fixedFees, fractionalFees);
        require(rc == 22);

        tokenAddress = createdAddress;
    }

    function createFractionalFee(
        uint32 numerator,
        uint32 denominator,
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
        uint32 autoRenewPeriod,
        address feeCollector
    ) public payable returns (address tokenAddress) {
        IHederaTokenService.HederaToken memory token = buildDefaultStructFrom(autoRenewAccount, autoRenewPeriod);
        IHederaTokenService.FractionalFee[] memory fractionalFees = new IHederaTokenService.FractionalFee[](0);
        IHederaTokenService.FixedFee[] memory fixedFees = new IHederaTokenService.FixedFee[](1);
        fixedFees[0] = createFixedSelfDenominatedFee(10, feeCollector);

        (int rc, address createdAddress) =
            super.createFungibleTokenWithCustomFees(
              token, initialTotalSupply, decimals, fixedFees, fractionalFees);

        require(rc == 22);

        tokenAddress = createdAddress;
    }

    function createFixedSelfDenominatedFee(uint32 amount, address feeCollector)
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
        uint32 autoRenewPeriod
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
    function makeRenewableToken(
        address autoRenewAccount,
        uint32 autoRenewPeriod
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

        (int rc, address createdAddress) =
            HederaTokenService.createFungibleToken(token, uint64(10000), uint32(1));
        require(rc == 22);

        tokenAddress = createdAddress;
    }
}
