# Metadata management via smart contracts

## Purpose

HIP-646/657/765 introduces a new metadata field for Fungible Tokens (FT), Non-Fungible Tokens (NFT) and METADATA key for
updating metadata. However, these features are not supported by smart contracts.
This proposal aims to enhance Hedera Smart Contract Service (HSCS) by exposing HIP-646/657/765 features.

## Goals

Allow users to create, update and get info for metadata and metadata key using smart contracts
1. Extend `HederaToken` struct with `bytes metadata` field in `IHederaTokenService.sol`
2. Extend `TokenKey` struct with comment that the 7th bit will be metadata key in `IHederaTokenService.sol`
3. Add new versions of the create and update functions in Translators and Decoders
4. Add new functions for view operations: `getFungibleTokenInfoWithMetadata(address token)`,
`getNonFungibleTokenInfoWithMetadata(address token, int64 serialNumber)`, `getTokenInfoWithMetadata(address token)`
5. Add new function `updateNftsMetadata(address token, int64[] serialNumbers, bytes metadata)` dispatching to
TokenUpdateNfts HAPI operation

## Non Goals

- The implementation of the HAPI operation, as it is already an existing feature.

## Architecture

The extended structs will change the signature of functions. However the old versions of `HederaTokenService.sol`
will continue working. To achieve this we will add new versions of the functions in Translators and Decoders.

| Function Selector |                                    Function Signature                                     |
|-------------------|-------------------------------------------------------------------------------------------|
| 7cb8323a          | `createFungibleToken(HederaToken, uint, uint)`                                            |
| 5ac3e67a          | `createFungibleTokenWithCustomFees(HederaToken, uint, uint, FixedFee[], FractionalFee[])` |
| c5bc16bc          | `createNonFungibleTokenWithCustomFees(HederaToken, FixedFee[], RoyaltyFee[])`             |
| 54c832a5          | `updateTokenInfo(address, HederaToken)`                                                   |

We need to add new functions for view calls since they are returning extended structs and we want old
versions of `HederaTokenService.sol` functions to continue working

| Function Selector |                            Function Signature                            |
|-------------------|--------------------------------------------------------------------------|
| 4448ed25          | `getFungibleTokenInfoWithMetadata(address token)`                        |
| 8284a7cf          | `getTokenInfoWithMetadata(address token)`                                |
| ded70b6a          | `getNonFungibleTokenInfoWithMetadata(address token, int64 serialNumber)` |

We need to add a new function for the TokenUpdateNfts HAPI operation

| Function Selector |                             Function Signature                             |
|-------------------|----------------------------------------------------------------------------|
| 3299b72c          | `updateNftsMetadata(address token, int64[] serialNumbers, bytes metadata)` |

## Acceptance Tests

### Positive Tests

- Verify `createFungibleToken` creates a fungible token with metadata
- Verify `createFungibleTokenWithCustomFees` creates a fungible token with metadata
- Verify `createNonFungibleToken` creates a non-fungible token with metadata
- Verify `createNonFungibleTokenWithCustomFees` creates a non-fungible token with metadata
- Verify `updateTokenInfo` updates token info with metadata
- Verify `getFungibleTokenInfoWithMetadata` returns the correct metadata for a fungible token
- Verify `getTokenInfoWithMetadata` returns the correct metadata for a token
- Verify `getNonFungibleTokenInfoWithMetadata` returns the correct metadata for a non-fungible token
- Verify `createFungibleToken` creates a fungible token with older version of `HederaTokenService.sol`
- Verify `createFungibleTokenWithCustomFees` creates a fungible token with older version of `HederaTokenService.sol`
- Verify `createNonFungibleToken` creates a non-fungible token with older version of `HederaTokenService.sol`
- Verify `createNonFungibleTokenWithCustomFees` creates a non-fungible token with older version of `HederaTokenService.sol`
- Verify `updateTokenInfo` updates token info with older version of `HederaTokenService.sol`
- Verify `updateTokenKeys` updates token metadata when metadata key is set
- Verify `createFungibleToken` creates a fungible token with metadata and metadata key
- Verify `createFungibleTokenWithCustomFees` creates a fungible token with metadata and metadata key
- Verify `createNonFungibleToken` creates a non-fungible token with metadata and metadata key
- Verify `createNonFungibleTokenWithCustomFees` creates a non-fungible token with metadata and metadata key
- Verify `updateTokenInfo` updates token info with metadata and metadata key
- Verify `getFungibleTokenInfoWithMetadata` returns the correct metadata for a fungible token with metadata key
- Verify `getTokenInfoWithMetadata` returns the correct metadata for a token with metadata key
- Verify `getNonFungibleTokenInfoWithMetadata` returns the correct metadata for a non-fungible token with metadata key
- Verify `updateNftsMetadata` updates metadata for multiple NFTs
- Verify `updateNftsMetadata` updates metadata for single NFT

### Negative Tests

- Verify `updateTokenInfo` fails to update token info with metadata when metadata key is not set
- Verify `updateTokenInfo` fails to update token info with metadata when metadata key is different
- Verify `updateNftsMetadata` fails to update metadata for multiple NFTs when metadata key is not set
- Verify `updateNftsMetadata` fails to update metadata for multiple NFTs when metadata key is different
