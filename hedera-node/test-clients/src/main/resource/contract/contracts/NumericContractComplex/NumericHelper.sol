// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;

import "./IHederaTokenService.sol";

/*´:°•.°+.*•´.*:˚.°*.˚•´.°:°•.°•.*•´.*:˚.°*.˚•´.°:°•.°+.*•´.*:*/
/*            Selectors and Structs with Versions             */
/*.•°:°.´+˚.*°.˚:*.´•*.+°.•°:´*.´•*.•°.•°:°.´:•˚°.*°.˚:*.´+°.•*/
interface NumericHelper {

    function createFungibleTokenV2(HederaTokenV2 memory token, uint64 initialTotalSupply, uint32 decimals) external payable
    returns (int64 responseCode, address tokenAddress);

    function createFungibleTokenV3(HederaTokenV3 memory token, int64 initialTotalSupply, int32 decimals) external payable
    returns (int64 responseCode, address tokenAddress);

    function createNonFungibleTokenV2(HederaTokenV2 memory token) external payable
    returns (int64 responseCode, address tokenAddress);

    function createNonFungibleTokenV3(HederaTokenV3 memory token) external payable
    returns (int64 responseCode, address tokenAddress);

    function createFungibleTokenWithCustomFeesV2(
        IHederaTokenService.HederaToken memory token,
        uint64 initialTotalSupply,
        uint32 decimals,
        IHederaTokenService.FixedFee[] memory fixedFees,
        IHederaTokenService.FractionalFee[] memory fractionalFees
    ) external payable returns (int64 responseCode, address tokenAddress);

    function createFungibleTokenWithCustomFeesV3(
        IHederaTokenService.HederaToken memory token,
        int64 initialTotalSupply,
        int32 decimals,
        FixedFeeV2[] memory fixedFees,
        FractionalFeeV2[] memory fractionalFees
    ) external payable returns (int64 responseCode, address tokenAddress);

    function createNonFungibleTokenWithCustomFeesV2(
        HederaTokenV2 memory token,
        IHederaTokenService.FixedFee[] memory fixedFees,
        IHederaTokenService.RoyaltyFee[] memory royaltyFees
    ) external payable returns (int64 responseCode, address tokenAddress);

    function createNonFungibleTokenWithCustomFeesV3(
        HederaTokenV3 memory token,
        FixedFeeV2[] memory fixedFees,
        RoyaltyFeeV2[] memory royaltyFees
    ) external payable returns (int64 responseCode, address tokenAddress);

    function updateTokenInfoV2(address token, HederaTokenV2 memory tokenInfo)
    external
    returns (int64 responseCode);

    function updateTokenInfoV3(address token, HederaTokenV3 memory tokenInfo)
    external
    returns (int64 responseCode);

    function cryptoTransfer(TransferList memory transferList, TokenTransferList[] memory tokenTransfers)
    external
    returns (int64 responseCode);

    struct TokenTransferList {
        address token;
        AccountAmount[] transfers;
        NftTransfer[] nftTransfers;
    }

    struct TransferList {
        AccountAmount[] transfers;
    }

    struct NftTransfer {
        // The solidity address of the sender
        address senderAccountID;

        // The solidity address of the receiver
        address receiverAccountID;

        // The serial number of the NFT
        int64 serialNumber;

        // If true then the transfer is expected to be an approved allowance and the
        // accountID is expected to be the owner. The default is false (omitted).
        bool isApproval;
    }

        struct AccountAmount {
        // The Account ID, as a solidity address, that sends/receives cryptocurrency or tokens
        address accountID;

        // The amount of  the lowest denomination of the given token that
        // the account sends(negative) or receives(positive)
        int64 amount;

        // If true then the transfer is expected to be an approved allowance and the
        // accountID is expected to be the owner. The default is false (omitted).
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