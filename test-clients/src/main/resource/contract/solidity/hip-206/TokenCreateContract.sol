// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.6.0 <0.9.0;
pragma experimental ABIEncoderV2;

import "./HederaTokenService.sol";
import "./IHederaTokenService.sol";
import "./HederaResponseCodes.sol";


/// This is a notional example of how the functions in HIP-358 could be used.
/// It is non-normative.
contract TokenCreateContract is HederaTokenService {

    using Bits for uint;

    // Create Fungible Token with no custom fees, with a user account as admin, contract as supply and pause key.
    function createFungible(address contractKey) external payable returns (address createdTokenAddress) {

        // create TokenKey of type adminKey with value inherited from called account
        IHederaTokenService.KeyValue memory adminKeyValue;
        adminKeyValue.inheritAccountKey = true;

        // create TokenKey of types supplyKey and pauseKey with value a contract address passed as function arg
        uint supplyPauseKeyType;
        IHederaTokenService.KeyValue memory supplyPauseKeyValue;
        // turn on bits corresponding to supply and pause key types
        supplyPauseKeyType = supplyPauseKeyType.setBit(4);
        supplyPauseKeyType = supplyPauseKeyType.setBit(6);
        // set the value of the key to the contract address passed as function arg
        supplyPauseKeyValue.contractId = contractKey;

        // instantiate the list of keys we'll use for token create
        IHederaTokenService.TokenKey[] memory keys = new IHederaTokenService.TokenKey[](2);
        keys[0] = IHederaTokenService.TokenKey (HederaTokenService.ADMIN_KEY_TYPE, adminKeyValue);
        keys[1] = IHederaTokenService.TokenKey (supplyPauseKeyType, supplyPauseKeyValue);

        IHederaTokenService.HederaToken memory myToken;
        myToken.name = "MyToken";
        myToken.symbol = "MTK";
        myToken.treasury = address(this);
        myToken.tokenKeys = keys;

        (int responseCode, address token) =
        HederaTokenService.createFungibleToken(myToken, 200, 8);

        if (responseCode != 22) {
            revert ();
        }

        createdTokenAddress = token;
    }

    // Create Fungible Token with custom fees, with a user account as admin, contract as supply and pause key.
    function createFungibleWithFees(address contractKey) external payable returns (address createdTokenAddress) {

        // create TokenKey of type adminKey with value inherited from called account
        IHederaTokenService.KeyValue memory adminKeyValue;
        adminKeyValue.inheritAccountKey = true;

        // create TokenKey of types supplyKey and pauseKey with value a contract address passed as function arg
        uint supplyPauseKeyType;
        IHederaTokenService.KeyValue memory supplyPauseKeyValue;
        // turn on bits corresponding to supply and pause key types
        supplyPauseKeyType = supplyPauseKeyType.setBit(4);
        supplyPauseKeyType = supplyPauseKeyType.setBit(6);
        // set the value of the key to the contract address passed as function arg
        supplyPauseKeyValue.contractId = contractKey;

        // instantiate the list of keys we'll use for token create
        IHederaTokenService.TokenKey[] memory keys = new IHederaTokenService.TokenKey[](2);
        keys[0] = IHederaTokenService.TokenKey (HederaTokenService.ADMIN_KEY_TYPE, adminKeyValue);
        keys[1] = IHederaTokenService.TokenKey (supplyPauseKeyType, supplyPauseKeyValue);

        // declare fee fields
        IHederaTokenService.FixedFee[] memory fixedFees;
        IHederaTokenService.FractionalFee[] memory fractionalFees;

        IHederaTokenService.HederaToken memory myToken;
        myToken.name = "MyToken";
        myToken.symbol = "MTK";
        myToken.treasury = address(this);
        myToken.tokenKeys = keys;

        (int responseCode, address token) =
        HederaTokenService.createFungibleTokenWithCustomFees(myToken, 200, 8, fixedFees, fractionalFees);

        if (responseCode != 22) {
            revert ();
        }

        createdTokenAddress = token;
    }

    function createNonFungibleToken() external payable returns (address createdTokenAddress){
        IHederaTokenService.HederaToken memory myToken;
        myToken.name = "MyNFT";
        myToken.symbol = "MNFT";
        myToken.treasury = address(this);

        // create the token through HTS with default expiry and royalty fees;
        (int responseCode, address token) =
        HederaTokenService.createNonFungibleToken(myToken);

        if (responseCode != 22) {
            revert ();
        }

        createdTokenAddress = token;
    }

    // Create NFT with a royalty fee, contract has the mint and admin key.
    function createNonFungibleTokenWithCustomFees(address feeCollector) external payable returns (address createdTokenAddress){

        // TokenKey of type adminKey and supplyKey with value this contract id
        uint adminSupplyKeyType;
        adminSupplyKeyType = adminSupplyKeyType.setBit(0); // turn on bit corresponding to admin key type
        adminSupplyKeyType = adminSupplyKeyType.setBit(4); // turn on bit corresponding to supply key type
        IHederaTokenService.KeyValue memory adminSupplyKeyValue;
        adminSupplyKeyValue.contractId = address(this);

        // instantiate the list of keys we'll use for token create
        IHederaTokenService.TokenKey[] memory keys = new IHederaTokenService.TokenKey[](1);
        keys[0] = IHederaTokenService.TokenKey (adminSupplyKeyType, adminSupplyKeyValue);

        // declare fee fields
        IHederaTokenService.FixedFee[] memory fixedFees;
        IHederaTokenService.RoyaltyFee[] memory royaltyFees = new IHederaTokenService.RoyaltyFee[](1);

        // create a royaltyFee for this NFT
        IHederaTokenService.RoyaltyFee memory tokenFee;
        tokenFee.numerator = 4;
        tokenFee.denominator = 5;
        tokenFee.feeCollector = feeCollector;

        royaltyFees[0] = tokenFee;

        IHederaTokenService.HederaToken memory myToken;
        myToken.name = "MyNFT";
        myToken.symbol = "MNFT";
        myToken.treasury = address(this);
        myToken.tokenKeys = keys;

        // create the token through HTS with default expiry and royalty fees;
        (int responseCode, address token) =
        HederaTokenService.createNonFungibleTokenWithCustomFees(myToken, fixedFees, royaltyFees);

        if (responseCode != 22) {
            revert ();
        }

        createdTokenAddress = token;
    }
}

library Bits {

    uint constant internal ONE = uint(1);

    // Sets the bit at the given 'index' in 'self' to '1'.
    // Returns the modified value.
    function setBit(uint self, uint8 index) internal pure returns (uint) {
        return self | ONE << index;
    }
}