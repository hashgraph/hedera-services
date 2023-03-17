// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;
pragma experimental ABIEncoderV2;

import "./FeeHelper.sol";
import "./IERC20.sol";
import "./ERC20.sol";
import "./IERC721.sol";
import "./IERC721Metadata.sol";
import "./HederaTokenService.sol";

contract TokenCreateContract is FeeHelper, HederaTokenService {

    string name = "tokenName";
    string symbol = "tokenSymbol";
    string memo = "memo";
    int64 initialTotalSupply = 200;
    int32 decimals = 8;

    // TEST-001
    function createTokenWithKeysAndExpiry(
        address treasury,
        bytes memory ed25519,
        bytes memory ecdsa,
        address contractID,
        address delegatableContractID,
        address autoRenewAccount,
        int64 autoRenewPeriod,
        address toAssociateWith
    ) public payable returns (address createdTokenAddress) {
        IHederaTokenService.TokenKey[] memory keys = new IHederaTokenService.TokenKey[](5);
        keys[0] = getSingleKey(KeyType.ADMIN, KeyType.KYC, KeyValueType.ED25519, ed25519);
        keys[1] = getSingleKey(KeyType.FREEZE, KeyType.WIPE, KeyValueType.SECP256K1, ecdsa);
        keys[2] = getSingleKey(KeyType.SUPPLY, KeyValueType.CONTRACT_ID, contractID);
        keys[3] = getSingleKey(KeyType.FEE, KeyValueType.DELEGETABLE_CONTRACT_ID, delegatableContractID);
        keys[4] = getSingleKey(KeyType.PAUSE, KeyValueType.INHERIT_ACCOUNT_KEY, "");

        IHederaTokenService.HederaToken memory token =
        createTokenWithExpiry(treasury, 0, autoRenewAccount, autoRenewPeriod, keys);

        (int responseCode, address tokenAddress) =
        HederaTokenService.createFungibleToken(token, initialTotalSupply, decimals);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }

        responseCode = HederaTokenService.associateToken(toAssociateWith, tokenAddress);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }

        (int responseCode2, int64 newTotalSupply) = HederaTokenService.burnToken(tokenAddress, 100, new int64[](0));
        if (responseCode2 != HederaResponseCodes.SUCCESS) {
            revert ();
        }

        createdTokenAddress = tokenAddress;
    }

    // TEST-002
    function createTokenWithAllCustomFeesAvailable(
        bytes memory ecdsaAdminKey,
        address feeCollector,
        address existingTokenAddress,
        address autoRenewAccount,
        int64 autoRenewPeriod
    ) public payable returns (address createdTokenAddress) {
        IHederaTokenService.TokenKey[] memory keys = new IHederaTokenService.TokenKey[](1);
        keys[0] = getSingleKey(KeyType.ADMIN, KeyValueType.SECP256K1, ecdsaAdminKey);

        IHederaTokenService.HederaToken memory token =
        createTokenWithExpiry(address(this), 0, autoRenewAccount, autoRenewPeriod, keys);

        IHederaTokenService.FixedFee[] memory fixedFees =
        createFixedFeesWithAllTypes(1, existingTokenAddress, feeCollector);
        IHederaTokenService.FractionalFee[] memory fractionalFees =
        createSingleFractionalFeeWithLimits(4, 5, 10, 30, true, feeCollector);

        (int responseCode, address tokenAddress) =
        HederaTokenService.createFungibleTokenWithCustomFees(token, initialTotalSupply, decimals,
            fixedFees, fractionalFees);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }

        createdTokenAddress = tokenAddress;
    }

    // TEST-003
    function createNFTTokenWithKeysAndExpiry(
        address treasury,
        bytes memory ed25519,
        address autoRenewAccount,
        int64 autoRenewPeriod
    ) public payable returns (address createdTokenAddress) {
        IHederaTokenService.TokenKey[] memory keys = getAllTypeKeys(KeyValueType.ED25519, ed25519);
        IHederaTokenService.HederaToken memory token =
        createTokenWithExpiry(treasury, 0, autoRenewAccount, autoRenewPeriod, keys);
        token.tokenSupplyType = true;
        token.maxSupply = 10;
        token.freezeDefault = true;

        (int responseCode, address tokenAddress) = HederaTokenService.createNonFungibleToken(token);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }

        createdTokenAddress = tokenAddress;
    }


    // TEST-004
    function createNonFungibleTokenWithCustomFees(
        address contractIdKey,
        address feeCollectorAndTreasury,
        address existingTokenAddress,
        address autoRenewAccount,
        int64 autoRenewPeriod,
        bytes memory ed25519
    )
    public payable returns (address createdTokenAddress) {
        IHederaTokenService.TokenKey[] memory keys = new IHederaTokenService.TokenKey[](2);
        keys[0] = getSingleKey(KeyType.ADMIN, KeyValueType.CONTRACT_ID, contractIdKey);
        keys[1] = getSingleKey(KeyType.SUPPLY, KeyValueType.ED25519, ed25519);

        IHederaTokenService.HederaToken memory token =
        createTokenWithExpiry(feeCollectorAndTreasury, 0, autoRenewAccount, autoRenewPeriod, keys);
        token.tokenSupplyType = true;
        token.maxSupply = 400;

        IHederaTokenService.RoyaltyFee[] memory royaltyFees =
        createRoyaltyFeesWithAllTypes(4, 5, 10, existingTokenAddress, feeCollectorAndTreasury);

        (int responseCode, address tokenAddress) =
        HederaTokenService.createNonFungibleTokenWithCustomFees(
            token, new IHederaTokenService.FixedFee[](0), royaltyFees);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }

        createdTokenAddress = tokenAddress;
    }


    // TEST-005
    function createTokenThenQueryAndTransfer(
        bytes memory ed25519AdminKey,
        address autoRenewAccount,
        int64 autoRenewPeriod
    ) public payable returns (address createdTokenAddress) {
        IHederaTokenService.TokenKey[] memory keys = new IHederaTokenService.TokenKey[](3);
        keys[0] = getSingleKey(KeyType.ADMIN, KeyValueType.ED25519, ed25519AdminKey);
        keys[1] = getSingleKey(KeyType.SUPPLY, KeyValueType.CONTRACT_ID, address(this));
        keys[2] = getSingleKey(KeyType.PAUSE, KeyValueType.CONTRACT_ID, address(this));

        IHederaTokenService.HederaToken memory token =
        createTokenWithExpiry(address(this), 0, autoRenewAccount, autoRenewPeriod, keys);

        (int responseCode, address tokenAddress) =
        HederaTokenService.createFungibleToken(token, 30, 8);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }

        createdTokenAddress = tokenAddress;

        string memory actualName = ERC20(tokenAddress).name();
        if (keccak256(bytes(actualName)) != keccak256(bytes(name))) {
            revert ("Name is not correct");
        }

        uint totalSupply = ERC20(tokenAddress).totalSupply();
        if (totalSupply != 30) {
            revert ("Total supply is not correct");
        }

        bool success = IERC20(tokenAddress).transfer(autoRenewAccount, 20);
        if (!success) {
            revert ("Transfer failed!");
        }
    }

    // TEST-006
    function createNonFungibleTokenThenQuery(
        address contractIdAndFeeCollector,
        address autoRenewAccount,
        int64 autoRenewPeriod
    ) public payable returns (address createdTokenAddress) {
        IHederaTokenService.TokenKey[] memory keys = new IHederaTokenService.TokenKey[](2);
        keys[0] = getSingleKey(KeyType.ADMIN, KeyValueType.CONTRACT_ID, contractIdAndFeeCollector);
        keys[1] = getSingleKey(KeyType.SUPPLY, KeyValueType.CONTRACT_ID, contractIdAndFeeCollector);

        IHederaTokenService.HederaToken memory token =
        createTokenWithExpiry(address(this), 0, autoRenewAccount, autoRenewPeriod, keys);

        IHederaTokenService.RoyaltyFee[] memory royaltyFees =
        createSingleRoyaltyFee(4, 5, contractIdAndFeeCollector);


        (int responseCode, address tokenAddress) =
        HederaTokenService.createNonFungibleTokenWithCustomFees(token, new IHederaTokenService.FixedFee[](0), royaltyFees);
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }

        createdTokenAddress = tokenAddress;

        string memory actualName = IERC721Metadata(tokenAddress).name();
        if (keccak256(bytes(actualName)) != keccak256(bytes(name))) {
            revert ("Name is not correct");
        }

        string memory actualSymbol = IERC721Metadata(tokenAddress).symbol();
        if (keccak256(bytes(actualSymbol)) != keccak256(bytes(symbol))) {
            revert ("Symbol is not correct");
        }
    }

    // TEST-007
    function createTokenWithEmptyKeysArray(
        address autoRenewAccount,
        int64 autoRenewPeriod
    ) public payable returns (address createdTokenAddress) {
        IHederaTokenService.TokenKey[] memory keys = new IHederaTokenService.TokenKey[](2);

        IHederaTokenService.HederaToken memory token =
        createTokenWithExpiry(address(this), 0, autoRenewAccount, autoRenewPeriod, keys);

        (int responseCode, address tokenAddress) =
        HederaTokenService.createFungibleToken(token, initialTotalSupply, decimals);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }

        createdTokenAddress = tokenAddress;
    }

    // TEST-008
    function createTokenWithKeyWithMultipleValues(
        address autoRenewAccount,
        int64 autoRenewPeriod
    ) public payable returns (address createdTokenAddress) {
        // create the invalid key
        IHederaTokenService.TokenKey[] memory keys = new IHederaTokenService.TokenKey[](1);
        IHederaTokenService.TokenKey memory invalidKey;
        invalidKey.keyType = 4;
        IHederaTokenService.KeyValue memory invalidKeyValue;
        invalidKeyValue.contractId = address(this);
        invalidKeyValue.inheritAccountKey = true;
        invalidKey.key = invalidKeyValue;
        keys[0] = invalidKey;

        IHederaTokenService.HederaToken memory token =
        createTokenWithExpiry(address(this), 0, autoRenewAccount, autoRenewPeriod, keys);

        (int responseCode, address tokenAddress) =
        HederaTokenService.createFungibleToken(token, initialTotalSupply, decimals);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }

        createdTokenAddress = tokenAddress;
    }

    // TEST-009
    function createTokenWithInvalidFixedFee(
        bytes memory ecdsaAdminKey,
        address feeCollector,
        address autoRenewAccount,
        int64 autoRenewPeriod
    ) public payable returns (address createdTokenAddress) {
        IHederaTokenService.TokenKey[] memory keys = new IHederaTokenService.TokenKey[](1);
        keys[0] = getSingleKey(KeyType.ADMIN, KeyValueType.SECP256K1, ecdsaAdminKey);

        IHederaTokenService.HederaToken memory token =
        createTokenWithExpiry(address(this), 0, autoRenewAccount, autoRenewPeriod, keys);

        IHederaTokenService.FixedFee memory fixedFee =
        createFixedFeeWithInvalidFlags(1, feeCollector);
        IHederaTokenService.FixedFee[] memory fixedFees = new IHederaTokenService.FixedFee[](1);
        fixedFees[0] = fixedFee;

        (int responseCode, address tokenAddress) =
        HederaTokenService.createFungibleTokenWithCustomFees(token, initialTotalSupply, decimals,
            fixedFees, new IHederaTokenService.FractionalFee[](0));

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }

        createdTokenAddress = tokenAddress;
    }

    // TEST-010
    function createTokenWithEmptyTokenStruct() public payable returns (address createdTokenAddress) {
        IHederaTokenService.HederaToken memory token;

        (int responseCode, address tokenAddress) =
        HederaTokenService.createFungibleToken(token, 0, 0);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }

        createdTokenAddress = tokenAddress;
    }

    // TEST-011
    function createTokenWithInvalidExpiry(
        address autoRenewAccount,
        int64 autoRenewPeriod
    ) public payable returns (address createdTokenAddress) {
        IHederaTokenService.HederaToken memory token =
        createTokenWithExpiry(address(this), 0, autoRenewAccount, autoRenewPeriod, new IHederaTokenService.TokenKey[](0));
        IHederaTokenService.Expiry memory invalidExpiry;
        invalidExpiry.second = 55;
        token.expiry = invalidExpiry;

        (int responseCode, address tokenAddress) =
        HederaTokenService.createFungibleToken(token, 0, 0);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }

        createdTokenAddress = tokenAddress;
    }

    // TEST-012
    function createNonFungibleTokenWithInvalidRoyaltyFee(
        address contractIdKey,
        address feeCollectorAndTreasury,
        address existingTokenAddress,
        address autoRenewAccount,
        int64 autoRenewPeriod,
        bytes memory ed25519
    )
    public payable returns (address createdTokenAddress) {
        IHederaTokenService.TokenKey[] memory keys = new IHederaTokenService.TokenKey[](2);
        keys[0] = getSingleKey(KeyType.ADMIN, KeyValueType.CONTRACT_ID, contractIdKey);
        keys[1] = getSingleKey(KeyType.SUPPLY, KeyValueType.ED25519, ed25519);

        IHederaTokenService.HederaToken memory token =
        createTokenWithExpiry(feeCollectorAndTreasury, 0, autoRenewAccount, autoRenewPeriod, keys);
        token.tokenSupplyType = true;
        token.maxSupply = 400;

        IHederaTokenService.RoyaltyFee[] memory royaltyFees =
        createSingleRoyaltyFeeWithFallbackFee(4, 5, 0, existingTokenAddress, false, feeCollectorAndTreasury);

        (int responseCode, address tokenAddress) =
        HederaTokenService.createNonFungibleTokenWithCustomFees(token, new IHederaTokenService.FixedFee[](0), royaltyFees);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }

        createdTokenAddress = tokenAddress;
    }

    // TEST-020
    function delegateCallCreate(
        address treasury,
        address autoRenewAccount,
        int64 autoRenewPeriod
    ) public returns (address createdTokenAddress) {

        IHederaTokenService.TokenKey[] memory keys = new IHederaTokenService.TokenKey[](1);
        keys[0] = getSingleKey(KeyType.ADMIN, KeyValueType.INHERIT_ACCOUNT_KEY, "");

        IHederaTokenService.HederaToken memory token =
        createTokenWithExpiry(treasury, 0, autoRenewAccount, autoRenewPeriod, keys);

        (bool success, bytes memory result) = address(0x167).delegatecall(
            abi.encodeWithSelector(IHederaTokenService.createFungibleToken.selector, token, initialTotalSupply
        , decimals));

        if (!success) {
            revert ();
        }

        (int responseCode, address tokenAddress) =  abi.decode(result, (int,address));
        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }

        createdTokenAddress = tokenAddress;
    }


    /** --- HELPERS --- */

    function createTokenWithExpiry(
        address treasury,
        int64 second,
        address autoRenewAccount,
        int64 autoRenewPeriod,
        IHederaTokenService.TokenKey[] memory keys
    ) internal returns (IHederaTokenService.HederaToken memory token) {

        IHederaTokenService.Expiry memory expiry;
        expiry.second = second;
        expiry.autoRenewAccount = autoRenewAccount;
        expiry.autoRenewPeriod = autoRenewPeriod;

        token.name = name;
        token.symbol = symbol;
        token.treasury = treasury;
        token.tokenKeys = keys;
        token.expiry = expiry;
        token.memo = memo;
    }

    function createTokenWithDefaultKeysViaDelegateCall() external returns (address createdTokenAddress) {
        createdTokenAddress = createTokenViaDelegateCall(super.getDefaultKeys());
    }

    function createTokenWithInheritedSupplyKey() external returns (address createdTokenAddress) {
        createdTokenAddress = createToken(super.getCustomSingleTypeKeys(KeyType.SUPPLY, KeyValueType.INHERIT_ACCOUNT_KEY, ""));
    }

    function createTokenWithAllTypeKeys(KeyValueType keyValueType, bytes memory key) external returns (address createdTokenAddress) {
        createdTokenAddress = createToken(super.getAllTypeKeys(keyValueType, key));
    }

    function createTokenWithCustomSingleTypeKeys(KeyType keyType, KeyValueType keyValueType, bytes memory key) external returns (address createdTokenAddress) {
        createdTokenAddress = createToken(super.getCustomSingleTypeKeys(keyType, keyValueType, key));
    }

    function createTokenWithCustomDuplexTypeKeys(KeyType firstKeyType, KeyType secondKeyType, KeyValueType keyValueType, bytes memory key) external returns (address createdTokenAddress) {
        createdTokenAddress = createToken(super.getCustomDuplexTypeKeys(firstKeyType, secondKeyType, keyValueType, key));
    }

    function createTokenWithTokenFixedFee(int64 amount, address tokenId, address feeCollector) external returns (address createdTokenAddress) {
        createdTokenAddress = createTokenWithCustomFees(super.getDefaultKeys(), super.createSingleFixedFeeForToken(amount, tokenId, feeCollector), super.getEmptyFractionalFees());
    }

    function createTokenWithTokenFixedFees(int64 amount, address tokenId, address firstFeeCollector, address secondFeeCollector) external returns (address createdTokenAddress) {
        createdTokenAddress = createTokenWithCustomFees(super.getDefaultKeys(), super.createFixedFeesForToken(amount, tokenId, firstFeeCollector, secondFeeCollector), super.getEmptyFractionalFees());
    }

    function createTokenWithCorrectAndWrongTokenFixedFee(int64 amount, address tokenId, address feeCollector) external returns (address createdTokenAddress) {
        createdTokenAddress = createTokenWithCustomFees(super.getDefaultKeys(), super.createFixedFeesForToken(amount, tokenId, feeCollector, address(0)), super.getEmptyFractionalFees());
    }

    function createTokenWithHbarsFixedFee(int64 amount, address feeCollector) external returns (address createdTokenAddress) {
        createdTokenAddress = createTokenWithCustomFees(super.getDefaultKeys(), super.createSingleFixedFeeForHbars(amount, feeCollector), super.getEmptyFractionalFees());
    }

    function createTokenWithCurrentTokenFixedFee(int64 amount, address feeCollector) external returns (address createdTokenAddress) {
        createdTokenAddress = createTokenWithCustomFees(super.getDefaultKeys(), super.createSingleFixedFeeForCurrentToken(amount, feeCollector), super.getEmptyFractionalFees());
    }

    function createTokenWithAllTypesFixedFee(int64 amount, address tokenId, address feeCollector) external returns (address createdTokenAddress) {
        createdTokenAddress = createTokenWithCustomFees(super.getDefaultKeys(), super.createFixedFeesWithAllTypes(amount, tokenId, feeCollector), super.getEmptyFractionalFees());
    }

    function createTokenWithInvalidFlagsFixedFee(int64 amount, address feeCollector) external returns (address createdTokenAddress) {
        createdTokenAddress = createTokenWithCustomFees(super.getDefaultKeys(), super.createSingleFixedFeeWithInvalidFlags(amount, feeCollector), super.getEmptyFractionalFees());
    }

    function createTokenWithFixedFeeForTokenAndHbars(address tokenId, int64 amount, address feeCollector) external returns (address createdTokenAddress) {
        createdTokenAddress = createTokenWithCustomFees(super.getDefaultKeys(), super.createSingleFixedFeeWithTokenIdAndHbars(amount, tokenId, feeCollector), super.getEmptyFractionalFees());
    }

    function createTokenWithFractionalFee(int64 numerator, int64 denominator, bool netOfTransfers, address feeCollector) external returns (address createdTokenAddress) {
        createdTokenAddress = createTokenWithCustomFees(super.getDefaultKeys(), super.getEmptyFixedFees(), super.createSingleFractionalFee(numerator, denominator, netOfTransfers, feeCollector));
    }

    function createTokenWithFractionalFeeWithLimits(int64 numerator, int64 denominator, int64 minimumAmount, int64 maximumAmount,
        bool netOfTransfers, address feeCollector) external returns (address createdTokenAddress) {
        createdTokenAddress = createTokenWithCustomFees(super.getDefaultKeys(), super.getEmptyFixedFees(), super.createSingleFractionalFeeWithLimits(numerator, denominator, minimumAmount, maximumAmount, netOfTransfers, feeCollector));
    }

    function createTokenWithHbarFixedFeeAndFractionalFee(int64 amount, int64 numerator, int64 denominator,
        bool netOfTransfers, address fixedFeeCollector, address fractionalFeeCollector) external returns (address createdTokenAddress) {
        createdTokenAddress = createTokenWithCustomFees(super.getDefaultKeys(), super.createSingleFixedFeeForHbars(amount, fixedFeeCollector), super.createSingleFractionalFee(numerator, denominator, netOfTransfers, fractionalFeeCollector));
    }

    function createTokenWithNAmountHbarFixedFeesAndNAmountFractionalFees(uint8 numberOfFixedFees, uint8 numberOfFractionalFees, int64 amount, int64 numerator, int64 denominator, bool netOfTransfers,
        address fixedFeeCollector, address fractionalFeeCollector) external returns (address createdTokenAddress) {
        createdTokenAddress = createTokenWithCustomFees(super.getDefaultKeys(),
            super.createNAmountFixedFeesForHbars(numberOfFixedFees, amount, fixedFeeCollector), super.createNAmountFractionalFees(numberOfFractionalFees, numerator, denominator, netOfTransfers, fractionalFeeCollector));
    }

    function createTokenViaDelegateCall(IHederaTokenService.TokenKey[] memory keys) internal returns (address createdTokenAddress) {
        IHederaTokenService.HederaToken memory token;
        token.name = name;
        token.symbol = symbol;
        token.treasury = address(this);
        token.tokenKeys = keys;

        (bool success, bytes memory result) = address(0x167).call(
            abi.encodeWithSelector(IHederaTokenService.createFungibleToken.selector,
            token, initialTotalSupply, decimals));
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
        token.treasury = address(this);
        token.tokenKeys = keys;

        (int responseCode, address tokenAddress) =
        HederaTokenService.createFungibleToken(token, 200, 8);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }

        createdTokenAddress = tokenAddress;
    }

    function createFrozenToken(IHederaTokenService.TokenKey[] memory keys) internal returns (address createdTokenAddress) {
        IHederaTokenService.HederaToken memory token;
        token.name = name;
        token.symbol = symbol;
        token.treasury = address(this);
        token.tokenKeys = keys;
        token.freezeDefault = true;

        (int responseCode, address tokenAddress) =
        HederaTokenService.createFungibleToken(token, initialTotalSupply, decimals);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }

        createdTokenAddress = tokenAddress;
    }

    function createFrozenTokenWithDefaultKeys() external returns (address createdTokenAddress) {
        createdTokenAddress = createFrozenToken(super.getDefaultKeys());
    }

    function createTokenWithDefaultKeys() public payable returns (address createdTokenAddress) {
        createdTokenAddress = createToken(super.getDefaultKeys());
    }

    function createTokenWithDefaultExpiryAndEmptyKeys() public payable returns (address createdTokenAddress) {
        IHederaTokenService.HederaToken memory token;
        token.name = name;
        token.symbol = symbol;
        token.treasury = address(this);

        (int responseCode, address tokenAddress) =
        HederaTokenService.createFungibleToken(token, 200, 8);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }

        createdTokenAddress = tokenAddress;
    }

    function createTokenWithCustomFees(IHederaTokenService.TokenKey[] memory keys,
        IHederaTokenService.FixedFee[] memory fixedFees, IHederaTokenService.FractionalFee[] memory fractionalFees) internal returns (address createdTokenAddress) {
        IHederaTokenService.HederaToken memory token;
        token.name = name;
        token.symbol = symbol;
        token.treasury = address(this);
        token.tokenKeys = keys;

        (int responseCode, address tokenAddress) =
        HederaTokenService.createFungibleTokenWithCustomFees(token, initialTotalSupply, decimals,
            fixedFees,
            fractionalFees);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }

        createdTokenAddress = tokenAddress;
    }
}