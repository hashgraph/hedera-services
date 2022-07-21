// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;
pragma experimental ABIEncoderV2;

import "../HTSPrecompile/HederaTokenService.sol";
import "../HTSPrecompile/IHederaTokenService.sol";
import "../HTSPrecompile/HederaResponseCodes.sol";
import "../HTSPrecompile/ExpiryHelper.sol";
import "./BigContract.sol";

/// This is a notional example of how the functions in HIP-358 could be used.
/// It is non-normative.
contract AvgContract is ExpiryHelper {

    using Bits for uint;

    // create a fungible Token with no custom fees, with calling contract as
    // admin key, passed ED25519 key as supply and pause key.
    function createFungible(
        bytes memory ed25519Key,
        address autoRenewAccount,
        uint32 autoRenewPeriod
    ) external payable returns (address createdTokenAddress) {

        // instantiate the list of keys we'll use for token create
        IHederaTokenService.TokenKey[] memory keys = new IHederaTokenService.TokenKey[](2);

        // use the helper methods in KeyHelper to create basic keys
        keys[0] = createSingleKey(HederaTokenService.ADMIN_KEY_TYPE, KeyHelper.INHERIT_ACCOUNT_KEY, "");

        // create TokenKey of types supplyKey and pauseKey with value a contract address passed as function arg
        uint supplyPauseKeyType;
        IHederaTokenService.KeyValue memory supplyPauseKeyValue;
        // turn on bits corresponding to supply and pause key types
        supplyPauseKeyType = supplyPauseKeyType.setBit(4);
        supplyPauseKeyType = supplyPauseKeyType.setBit(6);
        // set the value of the key to the ed25519Key passed as function arg
        supplyPauseKeyValue.ed25519 = ed25519Key;
        keys[1] = IHederaTokenService.TokenKey (supplyPauseKeyType, supplyPauseKeyValue);

        IHederaTokenService.HederaToken memory myToken;
        myToken.name = "MyToken";
        myToken.symbol = "MTK";
        myToken.treasury = address(this);
        myToken.tokenKeys = keys;
        // create the expiry schedule for the token using ExpiryHelper
        myToken.expiry = createAutoRenewExpiry(autoRenewAccount, autoRenewPeriod);

        // call HTS precompiled contract, passing 200 as initial supply and 8 as decimals
        (int responseCode, address token) =
                HederaTokenService.createFungibleToken(myToken, 200, 8);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }

        createdTokenAddress = token;
    }

    // create NFT with royalty fees, contract has the mint and admin key
    function createNonFungibleTokenWithCustomFees(
        address contractIdKey,
        address feeCollectorAndTreasury,
        address existingTokenAddress,
        address autoRenewAccount,
        uint32 autoRenewPeriod
    ) external payable returns (address createdTokenAddress) {

        // TokenKey of type adminKey and supplyKey with value this contract id
        uint adminSupplyKeyType;
        adminSupplyKeyType = adminSupplyKeyType.setBit(0); // turn on bit corresponding to admin key type
        adminSupplyKeyType = adminSupplyKeyType.setBit(4); // turn on bit corresponding to supply key type
        IHederaTokenService.KeyValue memory adminSupplyKeyValue;
        adminSupplyKeyValue.contractId = contractIdKey;

        // instantiate the list of keys we'll use for token create
        IHederaTokenService.TokenKey[] memory keys = new IHederaTokenService.TokenKey[](1);
        keys[0] = IHederaTokenService.TokenKey (adminSupplyKeyType, adminSupplyKeyValue);

        // declare fees
        IHederaTokenService.RoyaltyFee[] memory royaltyFees = new IHederaTokenService.RoyaltyFee[](3);
        royaltyFees[0] = createRoyaltyFeeWithoutFallback(4, 5, feeCollectorAndTreasury);
        royaltyFees[1] = createRoyaltyFeeWithHbarFallbackFee(4, 5, 50, feeCollectorAndTreasury);
        royaltyFees[2] =
                createRoyaltyFeeWithTokenDenominatedFallbackFee(4, 5, 30, existingTokenAddress, feeCollectorAndTreasury);

        IHederaTokenService.HederaToken memory myToken;
        myToken.name = "MyNFT";
        myToken.symbol = "MNFT";
        myToken.treasury = feeCollectorAndTreasury;
        myToken.tokenKeys = keys;
        myToken.expiry = createAutoRenewExpiry(autoRenewAccount, autoRenewPeriod);

        // create the token through HTS with default expiry and royalty fees;
        (int responseCode, address token) =
                HederaTokenService.createNonFungibleTokenWithCustomFees(
                    myToken,
                    new IHederaTokenService.FixedFee[](0),
                    royaltyFees
                );

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }

        createdTokenAddress = token;
    }

      // create NFT with royalty fees, contract has the mint and admin key
    function createNonFungibleTokenWithCustomFees2(
        address contractIdKey,
        address feeCollectorAndTreasury,
        address existingTokenAddress,
        address autoRenewAccount,
        uint32 autoRenewPeriod
    ) external payable returns (address createdTokenAddress) {

        // TokenKey of type adminKey and supplyKey with value this contract id
        uint adminSupplyKeyType;
        adminSupplyKeyType = adminSupplyKeyType.setBit(0); // turn on bit corresponding to admin key type
        adminSupplyKeyType = adminSupplyKeyType.setBit(4); // turn on bit corresponding to supply key type
        IHederaTokenService.KeyValue memory adminSupplyKeyValue;
        adminSupplyKeyValue.contractId = contractIdKey;

        // instantiate the list of keys we'll use for token create
        IHederaTokenService.TokenKey[] memory keys = new IHederaTokenService.TokenKey[](1);
        keys[0] = IHederaTokenService.TokenKey (adminSupplyKeyType, adminSupplyKeyValue);

        // declare fees
        IHederaTokenService.RoyaltyFee[] memory royaltyFees = new IHederaTokenService.RoyaltyFee[](3);
        royaltyFees[0] = createRoyaltyFeeWithoutFallback(4, 5, feeCollectorAndTreasury);
        royaltyFees[1] = createRoyaltyFeeWithHbarFallbackFee(4, 5, 50, feeCollectorAndTreasury);
        royaltyFees[2] =
                createRoyaltyFeeWithTokenDenominatedFallbackFee(4, 5, 30, existingTokenAddress, feeCollectorAndTreasury);

        IHederaTokenService.HederaToken memory myToken;
        myToken.name = "MyNFT";
        myToken.symbol = "MNFT";
        myToken.treasury = feeCollectorAndTreasury;
        myToken.tokenKeys = keys;
        myToken.expiry = createAutoRenewExpiry(autoRenewAccount, autoRenewPeriod);

        // create the token through HTS with default expiry and royalty fees;
        (int responseCode, address token) =
                HederaTokenService.createNonFungibleTokenWithCustomFees(
                    myToken,
                    new IHederaTokenService.FixedFee[](0),
                    royaltyFees
                );

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }

        createdTokenAddress = token;
    }

      // create NFT with royalty fees, contract has the mint and admin key
    function createNonFungibleTokenWithCustomFees3(
        address contractIdKey,
        address feeCollectorAndTreasury,
        address existingTokenAddress,
        address autoRenewAccount,
        uint32 autoRenewPeriod
    ) external payable returns (address createdTokenAddress) {

        // TokenKey of type adminKey and supplyKey with value this contract id
        uint adminSupplyKeyType;
        adminSupplyKeyType = adminSupplyKeyType.setBit(0); // turn on bit corresponding to admin key type
        adminSupplyKeyType = adminSupplyKeyType.setBit(4); // turn on bit corresponding to supply key type
        IHederaTokenService.KeyValue memory adminSupplyKeyValue;
        adminSupplyKeyValue.contractId = contractIdKey;

        // instantiate the list of keys we'll use for token create
        IHederaTokenService.TokenKey[] memory keys = new IHederaTokenService.TokenKey[](1);
        keys[0] = IHederaTokenService.TokenKey (adminSupplyKeyType, adminSupplyKeyValue);

        // declare fees
        IHederaTokenService.RoyaltyFee[] memory royaltyFees = new IHederaTokenService.RoyaltyFee[](3);
        royaltyFees[0] = createRoyaltyFeeWithoutFallback(4, 5, feeCollectorAndTreasury);
        royaltyFees[1] = createRoyaltyFeeWithHbarFallbackFee(4, 5, 50, feeCollectorAndTreasury);
        royaltyFees[2] =
                createRoyaltyFeeWithTokenDenominatedFallbackFee(4, 5, 30, existingTokenAddress, feeCollectorAndTreasury);

        IHederaTokenService.HederaToken memory myToken;
        myToken.name = "MyNFT";
        myToken.symbol = "MNFT";
        myToken.treasury = feeCollectorAndTreasury;
        myToken.tokenKeys = keys;
        myToken.expiry = createAutoRenewExpiry(autoRenewAccount, autoRenewPeriod);

        // create the token through HTS with default expiry and royalty fees;
        (int responseCode, address token) =
                HederaTokenService.createNonFungibleTokenWithCustomFees(
                    myToken,
                    new IHederaTokenService.FixedFee[](0),
                    royaltyFees
                );

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }

        createdTokenAddress = token;
    }

  // create NFT with royalty fees, contract has the mint and admin key
    function createNonFungibleTokenWithCustomFees4(
        address contractIdKey,
        address feeCollectorAndTreasury,
        address existingTokenAddress,
        address autoRenewAccount,
        uint32 autoRenewPeriod
    ) external payable returns (address createdTokenAddress) {

        // TokenKey of type adminKey and supplyKey with value this contract id
        uint adminSupplyKeyType;
        adminSupplyKeyType = adminSupplyKeyType.setBit(0); // turn on bit corresponding to admin key type
        adminSupplyKeyType = adminSupplyKeyType.setBit(4); // turn on bit corresponding to supply key type
        IHederaTokenService.KeyValue memory adminSupplyKeyValue;
        adminSupplyKeyValue.contractId = contractIdKey;

        // instantiate the list of keys we'll use for token create
        IHederaTokenService.TokenKey[] memory keys = new IHederaTokenService.TokenKey[](1);
        keys[0] = IHederaTokenService.TokenKey (adminSupplyKeyType, adminSupplyKeyValue);

        // declare fees
        IHederaTokenService.RoyaltyFee[] memory royaltyFees = new IHederaTokenService.RoyaltyFee[](3);
        royaltyFees[0] = createRoyaltyFeeWithoutFallback(4, 5, feeCollectorAndTreasury);
        royaltyFees[1] = createRoyaltyFeeWithHbarFallbackFee(4, 5, 50, feeCollectorAndTreasury);
        royaltyFees[2] =
                createRoyaltyFeeWithTokenDenominatedFallbackFee(4, 5, 30, existingTokenAddress, feeCollectorAndTreasury);

        IHederaTokenService.HederaToken memory myToken;
        myToken.name = "MyNFT";
        myToken.symbol = "MNFT";
        myToken.treasury = feeCollectorAndTreasury;
        myToken.tokenKeys = keys;
        myToken.expiry = createAutoRenewExpiry(autoRenewAccount, autoRenewPeriod);

        // create the token through HTS with default expiry and royalty fees;
        (int responseCode, address token) =
                HederaTokenService.createNonFungibleTokenWithCustomFees(
                    myToken,
                    new IHederaTokenService.FixedFee[](0),
                    royaltyFees
                );

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }

        createdTokenAddress = token;
    }

    function createTokenWithDefaultExpiryAndEmptyKeys() public payable returns (address createdTokenAddress) {
        IHederaTokenService.HederaToken memory token;
        token.name = "name";
        token.symbol = "symbol";
        token.treasury = address(this);

        (int responseCode, address tokenAddress) =
        HederaTokenService.createFungibleToken(token, 200, 8);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }

        createdTokenAddress = tokenAddress;
    }
}