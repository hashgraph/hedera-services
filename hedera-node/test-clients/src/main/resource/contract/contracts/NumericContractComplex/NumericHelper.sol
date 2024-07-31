// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;

import "./IHederaTokenService.sol";

/*´:°•.°+.*•´.*:˚.°*.˚•´.°:°•.°•.*•´.*:˚.°*.˚•´.°:°•.°+.*•´.*:*/
/*            Selectors and Structs with Versions             */
/*.•°:°.´+˚.*°.˚:*.´•*.+°.•°:´*.´•*.•°.•°:°.´:•˚°.*°.˚:*.´+°.•*/

interface NumericHelperV3 {

    //V3
    function createFungibleToken(HederaTokenV3 memory token, int64 initialTotalSupply, int32 decimals) external payable
    returns (int64 responseCode, address tokenAddress);

    //V3
    function createNonFungibleToken(HederaTokenV3 memory token) external payable
    returns (int64 responseCode, address tokenAddress);

    //V3
    function createFungibleTokenWithCustomFees(
        IHederaTokenService.HederaToken memory token,
        int64 initialTotalSupply,
        int32 decimals,
        FixedFeeV2[] memory fixedFees,
        FractionalFeeV2[] memory fractionalFees
    ) external payable returns (int64 responseCode, address tokenAddress);

    //V3
    function createNonFungibleTokenWithCustomFees(
        HederaTokenV3 memory token,
        FixedFeeV2[] memory fixedFees,
        RoyaltyFeeV2[] memory royaltyFees
    ) external payable returns (int64 responseCode, address tokenAddress);

    //V3
    function updateTokenInfo(address token, HederaTokenV3 memory tokenInfo)
    external
    returns (int64 responseCode);
}

interface NumericHelperV2 {

    //V2
    function createFungibleToken(HederaTokenV2 memory token, uint64 initialTotalSupply, uint32 decimals) external payable
    returns (int64 responseCode, address tokenAddress);

    //V2
    function createNonFungibleToken(HederaTokenV2 memory token) external payable
    returns (int64 responseCode, address tokenAddress);

    //V2
    function createFungibleTokenWithCustomFees(
        IHederaTokenService.HederaToken memory token,
        uint64 initialTotalSupply,
        uint32 decimals,
        IHederaTokenService.FixedFee[] memory fixedFees,
        IHederaTokenService.FractionalFee[] memory fractionalFees
    ) external payable returns (int64 responseCode, address tokenAddress);

    //V2
    function createNonFungibleTokenWithCustomFees(
        HederaTokenV2 memory token,
        IHederaTokenService.FixedFee[] memory fixedFees,
        IHederaTokenService.RoyaltyFee[] memory royaltyFees
    ) external payable returns (int64 responseCode, address tokenAddress);

    //V2
    function updateTokenInfo(address token, HederaTokenV2 memory tokenInfo)
    external
    returns (int64 responseCode);

    //V2
    function cryptoTransfer(TransferList memory transferList, TokenTransferList[] memory tokenTransfers)
    external
    returns (int64 responseCode);
}

interface Structs {
    struct TokenTransferList {
        address token;
        AccountAmount[] transfers;
        NftTransfer[] nftTransfers;
    }

    struct TransferList {
        AccountAmount[] transfers;
    }

    struct NftTransfer {
        address senderAccountID;
        address receiverAccountID;
        int64 serialNumber;
        bool isApproval;
    }

        struct AccountAmount {
        address accountID;
        int64 amount;
        bool isApproval;
    }

    struct HederaTokenV2 {
        string name;
        string symbol;
        address treasury;
        string memo;
        bool tokenSupplyType;
        int64 maxSupply; // Changed from uint32 to int64
        bool freezeDefault;
        IHederaTokenService.TokenKey[] tokenKeys;
        IHederaTokenService.Expiry expiry;
    }

    struct HederaTokenV3 {
        string name;
        string symbol;
        address treasury;
        string memo;
        bool tokenSupplyType;
        int64 maxSupply; // Changed from uint32 to int64
        bool freezeDefault;
        IHederaTokenService.TokenKey[] tokenKeys;
        ExpiryV2 expiry;
    }

    struct ExpiryV2 {
        int64 second; // Changed from uint32 to int64
        address autoRenewAccount;
        int64 autoRenewPeriod; // Changed from uint32 to int64
    }

    struct FixedFeeV2 {
        int64 amount;
        address tokenId;
        bool useHbarsForPayment;
        bool useCurrentTokenForPayment;
        address feeCollector;
    }

    struct RoyaltyFeeV2 {
        int64 numerator;
        int64 denominator;
        int64 amount;
        address tokenId;
        bool useHbarsForPayment;
        address feeCollector;
    }

    struct FractionalFeeV2 {
        int64 numerator;
        int64 denominator;
        int64 minimumAmount;
        int64 maximumAmount;
        bool netOfTransfers;
        address feeCollector;
    }

}