# Update Hedera Token Service Smart Contract

## Purpose

In order to support updating the Token Custom Fees via smart contract calls, we need to expose a new function in the Hedera Token Service Smart Contract. This document will define the architecture and implementation of the `tokenFeeScheduleUpdate` function that will extend tha capabilities of the Hedera Token Service Smart Contract.

## References

[HIP-514](https://hips.hedera.com/hip/hip-514) - HIP that introduces token management via smart contracts.\
[HIP-206](https://hips.hedera.com/hip/hip-206) - HIP that enables the precompiled system contracts for Hedera Token Service.

## Goals

- Expose `tokenFeeScheduleUpdate` as a new function in the Hedera Token Service Smart Contract.
- Implement the needed HTS system contract classes to support the new function.

## Non Goals

- The implementation of the HAPI operation, as it is already an existing feature.

## Architecture

The architecture for token fee schedule update follows the existing framework defined for handling all calls to the HederaTokenService system contract in the modularization services and is described in more detail in the Implementation section below.

## Implementation

### New Struct Definitions

| Struct Name          | Description                                                      |
|----------------------|------------------------------------------------------------------|
| `TokenFeeSchedule`   | A struct that contains the tokenId and fee schedule information. |

We will add a new struct definition to the `IHederaTokenService` interface to support the new function.

```solidity
    struct TokenFeeSchedule {
        // Specifies ID of the token that would be updated
        address tokenId;

        // FixedFee to be updated
        FixedFee[] fixedFee;

        // FractionalFee to be updated
        FractionalFee[] fractionalFee;

        // RoyaltyFee to be updated
        RoyaltyFee[] royaltyFee;
    }
```


### New Function Implementations

| Function Selector Hash  | Function Signature                                     | Response                 |                                                                                                      | 
|-------------------------|--------------------------------------------------------|--------------------------|------------------------------------------------------------------------------------------------------|
| `0xe7498136`             | `tokenFeeScheduleUpdate(TokenFeeSchedule feeSchedule)` | `ResponseCode`           | The response code from the call                                                                      |


### Smart Contract Service Module

- TokenFeeScheduleUpdateTranslator
- TokenFeeScheduleUpdateDecoder

## Acceptance Tests

### BDD Tests

#### Positive Tests

- Verify that the `tokenFeeScheduleUpdate` function updates `FixedHbarFee` for a given token.
- Verify that the `tokenFeeScheduleUpdate` function updates `FixedHTSFee` for a given token.
- Verify that the `tokenFeeScheduleUpdate` function updates `FractionalFee` for a given token.
- Verify that the `tokenFeeScheduleUpdate` function updates `RoyaltyFee` for a given nft.

#### Negative Tests

- Verify that the `tokenFeeScheduleUpdate` function with empty `Fee Schedule` fails with `CUSTOM_SCHEDULE_ALREADY_HAS_NO_FEES` when the given token has no set fees.
- Verify that the `tokenFeeScheduleUpdate` function fails with `TOKEN_HAS_NO_FEE_SCHEDULE_KEY` when the `feeSchedule` key is not provided.
- Verify that the `tokenFeeScheduleUpdate` function fails with `CUSTOM_FEE_MUST_BE_POSITIVE` when the provided `feeSchedule` is with negative values.
- Verify that the `tokenFeeScheduleUpdate` function fails with `FRACTION_DIVIDES_BY_ZERO` when the provided `FractionalFee` denominator is zero.
- Verify that the `tokenFeeScheduleUpdate` function fails with `CUSTOM_FEES_LIST_TOO_LONG` when the provided `feeSchedule` exceeds 10 custom fees.
- Verify that the `tokenFeeScheduleUpdate` function fails with `INVALID_CUSTOM_FEE_COLLECTOR` when the provided `feeSchedule` has invalid fee collector account.
- Verify that the `tokenFeeScheduleUpdate` function fails with `INVALID_TOKEN_ID_IN_CUSTOM_FEES` when the provided `feeSchedule` has invalid fee token.
- Verify that the `tokenFeeScheduleUpdate` function fails with `TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR` when the provided `feeSchedule` has fee token not associated to the fee collector.
- Verify that the `tokenFeeScheduleUpdate` function fails with `CUSTOM_FEE_NOT_FULLY_SPECIFIED` when the provided `feeSchedule` is incomplete.

