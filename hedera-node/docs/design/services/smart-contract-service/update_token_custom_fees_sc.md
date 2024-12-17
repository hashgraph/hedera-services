# Update Token Custom Fees via Smart Contracts

## Purpose

In order to support updating the Token Custom Fees via smart contract calls, we need to expose new smart contract functions in the Hedera Smart Contract Service (HSCS) logic exposing Hedera Token Service (HTS) logic. This document will define the architecture and implementation of the `updateFungibleTokenCustomFees` and `updateNonFungibleTokenCustomFees` smart contract functions that will extend tha capabilities of the HSCS.

## References

[HIP-18](https://hips.hedera.com/hip/hip-18) - HIP that introduces token custom fees.\
[HIP-206](https://hips.hedera.com/hip/hip-206) - HIP that enables the precompiled system contracts for Hedera Token Service.\
[HIP-514](https://hips.hedera.com/hip/hip-514) - HIP that introduces token management via smart contracts.\
[HIP-1010](https://hips.hedera.com/hip/hip-1010) - HIP that introduces the updates of token custom fees via smart contracts.

## Goals

- Expose `updateFungibleTokenCustomFees` and `updateNonFungibleTokenCustomFees` as new functions in the Hedera Token Service Smart Contract.
- Implement the needed HTS system contract classes to support the new function.

## Non Goals

- The implementation of the HAPI operation, as it is already an existing feature.

## Architecture

The architecture for update token custom fees follows the existing framework defined for handling all calls to the HederaTokenService system contract in the modularization services and is described in more detail in the Implementation section below.

## Implementation

### New Function Implementations

New system contract functions must be added to the `IHederaTokenService` interface to support the updating the custom fees for fungible and non-fungible tokens.

| Function Selector Hash |                                                                                        Function Signature                                                                                         |    Response    |                                 |
|------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------|---------------------------------|
| `0xe780c5d3`           | `function updateFungibleTokenCustomFees(address token,  IHederaTokenService.FixedFee[] memory fixedFees, IHederaTokenService.FractionalFee[] memory fractionalFees) returns (int64 responseCode)` | `ResponseCode` | The response code from the call |
| `0x01f9eb7d`           | `function updateNonFungibleTokenCustomFees(address token, IHederaTokenService.FixedFee[] memory fixedFees, IHederaTokenService.RoyaltyFee[] memory royaltyFees) returns (int64 responseCode)`     | `ResponseCode` | The response code from the call |

### Smart Contract Service Module

- UpdateTokenCustomFeesTranslator - This class will be responsible for handling the `updateFungibleTokenCustomFees` and `updateNonFungibleTokenCustomFees` selectors and dispatching them to the corresponding HAPI calls.
- UpdateTokenCustomFeesDecoder - This class provides methods  and constants for decoding the given `HtsCallAttempt` into a `TokenFeeScheduleUpdateTransactionBody` for `TokenFeeScheduleUpdate` call.

Similar approaches would be the System Contracts for Update Token Info - `UpdateDecoder`and it's relative `UpdateTranslator`, `UpdateKeysTranslator` and `UpdateExpiryTranslator` classes.
We can also refer to the Wipe, Pause, Mint, Burn, Freeze and Associate system contracts, as they leverage the same approach.

## Security Implications

The newly added flows will adopt the HAPI authorization logic. In this way the transaction would be successful only if the given contract making the call is set as a `feeScheduleKey` to the token or a threshold key including the calling contract ID is set for the same.
We will apply the `TokenFeeScheduleUpdate` throttle mechanism.

## Acceptance Tests

### BDD Tests

#### Positive Tests

- Verify that the `updateFungibleTokenCustomFees` function updates `FixedHbarFee` for a given token.
- Verify that the `updateFungibleTokenCustomFees` function updates multiple `FixedHbarFees` for a given token.
- Verify that the `updateFungibleTokenCustomFees` function updates `FixedHTSFee` for a given token.
- Verify that the `updateFungibleTokenCustomFees` function updates multiple `FixedHTSFees` for a given token.
- Verify that the `updateFungibleTokenCustomFees` function updates `FixedHTSFee` with the same token as denominator.
- Verify that the `updateFungibleTokenCustomFees` function updates `FractionalFee` for a given token.
- Verify that the `updateFungibleTokenCustomFees` function updates `FractionalFee` for a given token with net of transfers enabled.
- Verify that the `updateFungibleTokenCustomFees` function updates `FractionalFee` for a given token with `min` and `max` amounts.
- Verify that the `updateFungibleTokenCustomFees` function updates multiple `FractionalFees` for a given token.
- Verify that the `updateFungibleTokenCustomFees` function updates both `FixedHbarFees` and `FractionalFees` for a given token.
- Verify that the `updateNonFungibleTokenCustomFees` function updates `RoyaltyFee` for a given nft.
- Verify that the `updateNonFungibleTokenCustomFees` function updates multiple `RoyaltyFees` for a given nft.
- Verify that the `updateNonFungibleTokenCustomFees` function updates `FixedHBARFee` for a given nft.
- Verify that the `updateNonFungibleTokenCustomFees` function updates `FixedHTSFee` for a given nft.
- Verify that the `updateNonFungibleTokenCustomFees` function updates both `RoyaltyFee` `FixedHTSFee` for a given nft.
- Verify that the `updateNonFungibleTokenCustomFees` function updates `RoyaltyFee` with `HBAR` fallback for a given nft.
- Verify that the `updateNonFungibleTokenCustomFees` function updates `RoyaltyFee` with `HTS` token fallback for a given nft.

#### Negative Tests

- Verify that the `updateFungibleTokenCustomFees` function with empty `Fee Schedule` fails with `CUSTOM_SCHEDULE_ALREADY_HAS_NO_FEES` when the given token has no set fees.
- Verify that the `updateNonFungibleTokenCustomFees` function with empty `Fee Schedule` fails with `CUSTOM_SCHEDULE_ALREADY_HAS_NO_FEES` when the given token has no set fees.
- Verify that the `updateFungibleTokenCustomFees` function fails with `TOKEN_HAS_NO_FEE_SCHEDULE_KEY` when the `feeSchedule` key is not provided.
- Verify that the `updateNonFungibleTokenCustomFees` function fails with `TOKEN_HAS_NO_FEE_SCHEDULE_KEY` when the `feeSchedule` key is not provided.
- Verify that the `updateFungibleTokenCustomFees` function fails with `CUSTOM_FEE_MUST_BE_POSITIVE` when the provided `feeSchedule` is with negative values.
- Verify that the `updateNonFungibleTokenCustomFees` function fails with `CUSTOM_FEE_MUST_BE_POSITIVE` when the provided `feeSchedule` is with negative values.
- Verify that the `updateFungibleTokenCustomFees` function fails with `FRACTION_DIVIDES_BY_ZERO` when the provided `FractionalFee` denominator is zero.
- Verify that the `updateFungibleTokenCustomFees` function fails with `CUSTOM_FEES_LIST_TOO_LONG` when the provided `feeSchedule` exceeds 10 custom fees.
- Verify that the `updateNonFungibleTokenCustomFees` function fails with `CUSTOM_FEES_LIST_TOO_LONG` when the provided `feeSchedule` exceeds 10 custom fees.
- Verify that the `updateFungibleTokenCustomFees` function fails with `INVALID_CUSTOM_FEE_COLLECTOR` when the provided `feeSchedule` has invalid fee collector account.
- Verify that the `updateNonFungibleTokenCustomFees` function fails with `INVALID_CUSTOM_FEE_COLLECTOR` when the provided `feeSchedule` has invalid fee collector account.
- Verify that the `updateFungibleTokenCustomFees` function fails with `INVALID_TOKEN_ID_IN_CUSTOM_FEES` when the provided `feeSchedule` has invalid fee token.
- Verify that the `updateNonFungibleTokenCustomFees` function fails with `INVALID_TOKEN_ID_IN_CUSTOM_FEES` when the provided `feeSchedule` has invalid fee token.
- Verify that the `updateFungibleTokenCustomFees` function fails with `TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR` when the provided `feeSchedule` has fee token not associated to the fee collector.
- Verify that the `updateNonFungibleTokenCustomFees` function fails with `TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR` when the provided `feeSchedule` has fee token not associated to the fee collector.
- Verify that the `updateNonFungibleTokenCustomFees` function fails with `CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON` when the provided `feeSchedule` tries to set `FixedHTSFee` with the same token as denominator.
