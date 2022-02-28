// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.6.0 <0.9.0;
pragma experimental ABIEncoderV2;

import "./HederaTokenService.sol";
import "./IHederaTokenService.sol";
import "./HederaResponseCodes.sol";

contract TokenCreateContract is HederaTokenService {

    using Bits for uint;

    // Create Fungible Token with no custom fees, with a user account as admin, contract as supply and pause key.
    function createFungible(address contractKey) external returns (address createdTokenAddress) {

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

        (bool success, address token, bytes memory errorMessage) =
        HederaTokenService.createFungibleToken(myToken, 200, 8);

        if (!success) {
            revert (abi.decode(errorMessage, (string)));
        }

        createdTokenAddress = token;
    }

    // Create Fungible Token with custom fees, with a user account as admin, contract as supply and pause key.
    function createFungibleWithFees(address contractKey) external returns (address createdTokenAddress) {

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

        (bool success, address token, bytes memory errorMessage) =
        HederaTokenService.createFungibleTokenWithCustomFees(myToken, 200, 8, fixedFees, fractionalFees);

        if (!success) {
            revert (abi.decode(errorMessage, (string)));
        }

        createdTokenAddress = token;
    }

    function createNonFungibleToken() external returns (address createdTokenAddress){
        IHederaTokenService.HederaToken memory myToken;
        myToken.name = "MyNFT";
        myToken.symbol = "MNFT";
        myToken.treasury = address(this);

        // create the token through HTS with default expiry and royalty fees;
        (bool success, address token, bytes memory errorMessage) =
        HederaTokenService.createNonFungibleToken(myToken);

        if (!success) {
            revert (abi.decode(errorMessage, (string)));
        }

        createdTokenAddress = token;
    }

    // Create NFT with a royalty fee, contract has the mint and admin key.
    function createNonFungibleTokenWithCustomFees(address feeCollector) external returns (address createdTokenAddress){

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
        IHederaTokenService.FractionalFee[] memory fractionalFees;
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
        (bool success, address token, bytes memory errorMessage) =
        HederaTokenService.createNonFungibleTokenWithCustomFees(myToken, fixedFees, fractionalFees, royaltyFees);

        if (!success) {
            revert (abi.decode(errorMessage, (string)));
        }

        createdTokenAddress = token;
    }

    // update a token with a 3 required out of 5 possible signatures threshold key
    function updateTokenWithThresholdKeys(address token, bytes memory ed25519, bytes memory ECDSA_secp256k1) external {

        // create all possible values for the threshold key
        IHederaTokenService.KeyValue[] memory possibleKeyValues = new IHederaTokenService.KeyValue[](5);

        IHederaTokenService.KeyValue memory value1;
        value1.contractId = address(this);

        IHederaTokenService.KeyValue memory value2;
        value2.inheritAccountKey = true;

        IHederaTokenService.KeyValue memory value3;
        value3.ed25519 = ed25519;

        IHederaTokenService.KeyValue memory value4;
        value4.ECDSA_secp256k1 = ECDSA_secp256k1;

        IHederaTokenService.KeyValue memory value5;
        value5.delegatableContractId = address(this);

        possibleKeyValues[0] = value1;
        possibleKeyValues[1] = value2;
        possibleKeyValues[2] = value3;
        possibleKeyValues[3] = value4;
        possibleKeyValues[4] = value5;

        // turn on bit corresponding to admin key type
        uint adminKeyType;
        adminKeyType = adminKeyType.setBit(0);

        // create the threshold key struct
        IHederaTokenService.TresholdTokenKey memory thresholdKey;
        thresholdKey.keyType = adminKeyType;
        thresholdKey.threshold = 3;
        thresholdKey.keys = possibleKeyValues;

        IHederaTokenService.TresholdTokenKey[] memory thresholdKeysList = new IHederaTokenService.TresholdTokenKey[](1);
        thresholdKeysList[0] = thresholdKey;

        (bool success, bytes memory errorMessage) = HederaTokenService.setTokenThresholdKeys(token, thresholdKeysList);

        if (!success){
            revert (abi.decode(errorMessage, (string)));
        }
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