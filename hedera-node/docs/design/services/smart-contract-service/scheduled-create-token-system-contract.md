# Scheduled Token Create System Contract

## Purpose

[HIP-755](https://hips.hedera.com/hip/hip-755) defines a new system contract that allows accounts and contracts to interact with a new schedule service system contract enabling
[HIP-756](https://hips.hedera.com/hip/hip-756) to define new system contract functions to schedule token creation in order for a smart contract to be able to designate another account for autoRenew and/or treasury.
This document will define the architecture and implementation of this functionality.

## Goals

- Provide the users with the ability to schedule token creation and updates.
- Provide the ability to retrieve the information for the scheduled creation/update.
- Provide the ability to retrieve the newly created token address via scheduled transaction.

## Non Goals

- The implementation of the Schedule Service logic as it is already done
- The implementation of the HSSSystemContract infrastructure wiring as it is already done

## Architecture and Implementation

The architecture for the scheduled creations follows the existing framework defined for handling all calls to the HederaScheduleService system contract in the modularization services and is described in more detail in the section below.

### New Solidity Functions

| Function Selector Hash |                                                                                   Short Selector                                                                                    |                                                                                                                                                    Function Signature                                                                                                                                                    | HAPI operation  |                              Description                              |
|------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------|-----------------------------------------------------------------------|
| `0xe780c5d3`           | `getScheduledFungibleTokenCreateTransaction(address scheduleAddress)`                                                                                                               | `function getScheduledFungibleTokenCreateTransaction(address scheduleAddress) returns (int64 responseCode, FungibleTokenInfo memory tokenInfo)`                                                                                                                                                                          | ScheduleGetInfo | Retrieve information for the scheduled token create                   |
| `0x14749042`           | `getScheduledNonFungibleTokenCreateTransaction(address scheduleAddress)`                                                                                                            | `function getScheduledNonFungibleTokenCreateTransaction(address scheduleAddress) returns (int64 responseCode, NonFungibleTokenInfo memory tokenInfo)`                                                                                                                                                                    | ScheduleGetInfo | Retrieve information for the scheduled nft create                     |
| `0xf616ec93`           | `getScheduledTokenAddress(address scheduleAddress)`                                                                                                                                 | `function getScheduledTokenAddress(address scheduleAddress) returns (int64 responseCode, address tokenAddress)`                                                                                                                                                                                                          | ScheduleGetInfo | Retrieve the token address for an executed scheduled token/nft create |
| `0x4742876e`           | `scheduleCreateFungibleToken(HederaToken memory token,int64 initialTotalSupply, int32 decimals)`                                                                                    | `function scheduleCreateFungibleToken((string,string,address,string,bool,int64,bool,(uint256,(bool,address,bytes,bytes,address))[],(int64,address,int64)),int64,int32) returns (int64 responseCode, address scheduleAddress)`                                                                                            | ScheduleCreate  | Schedule a token creation                                             |
| `0xa001e7d2`           | `scheduleCreateFungibleTokenWithCustomFees(HederaToken memory token, int64 initialTotalSupply, int32 decimals, FixedFee[] memory fixedFees, FractionalFee[] memory fractionalFees)` | `function scheduleCreateFungibleTokenWithCustomFees((string,string,address,string,bool,int64,bool,(uint256,(bool,address,bytes,bytes,address))[],(int64,address,int64)),int64,int32,(int64,address,bool,bool,address)[],(int64,int64,int64,int64,bool,address)[]) returns (int64 responseCode, address scheduleAddress)` | ScheduleCreate  | Schedule a token creation with custom fees                            |
| `0xbbaa57c2`           | `scheduleCreateNonFungibleToken(HederaToken memory token)`                                                                                                                          | `function scheduleCreateNonFungibleToken((string,string,address,string,bool,int64,bool,(uint256,(bool,address,bytes,bytes,address))[],(int64,address,int64))) returns (int64 responseCode, address scheduleAddress)`                                                                                                     | ScheduleCreate  | Schedule a nft creation                                               |
| `0x228fa74a`           | `scheduleCreateNonFungibleTokenWithCustomFees(HederaToken memory token, FixedFee[] memory fixedFees, RoyaltyFee[] memory royaltyFees)`                                              | `function scheduleCreateNonFungibleTokenWithCustomFees((string,string,address,string,bool,int64,bool,(uint256,(bool,address,bytes,bytes,address))[],(int64,address,int64)),(int64,address,bool,bool,address)[],(int64,int64,int64,address,bool,address)[]) returns (int64 responseCode, address scheduleAddress)`        | ScheduleCreate  | Schedule a nft creation with custom fees                              |
| `0xc42a9a17`           | `scheduleUpdateTokenInfo(address token, HederaToken memory tokenInfo)`                                                                                                              | `function scheduleUpdateTokenInfo(address,(string,string,address,string,bool,int64,bool,(uint256,(bool,address,bytes,bytes,address))[],(int64,address,int64))) returns (int64 responseCode, address scheduleAddress)`                                                                                                    | ScheduleCreate  | Schedule token update                                                 |

### System Contract Module

- `ScheduledTokenCreateTranslator` - This class will be responsible for handling the `scheduleCreateFungibleToken`, `scheduleCreateFungibleTokenWithCustomFees`, `scheduleCreateNonFungibleToken`, `scheduleCreateNonFungibleTokenWithCustomFees` and selectors and dispatching them to the Schedule Service.
- `ScheduledTokenCreateDecoder` - This class provides methods and constants for decoding the given `HssCallAttempt` into a `ScheduleCreateTransactionBody`.
- `GetScheduledInfoTranslator` - This class will be responsible for handling the `getScheduledFungibleTokenCreateTransaction`, `getScheduledNonFungibleTokenCreateTransaction` and `getScheduledTokenAddress` selectors and dispatching them to the Schedule Service.
- `GetScheduledTokenInfoCall` - This class provides methods and constants for decoding the `ScheduleGetInfoResponse` into a `PricedResult`.
- `GetScheduledNonFungibleTokenInfoCall` - This class provides methods and constants for decoding the `ScheduleGetInfoResponse` into a `PricedResult`.
- `GetScheduledTokenAddressCall` - This class provides methods and constants for decoding the `ScheduleGetInfoResponse` into a `PricedResult`.
- `ScheduledUpdateTokenInfoTranslator` - This class will be responsible for handling the `scheduleUpdateTokenInfo` selector and dispatching it to the Schedule Service.
- `ScheduledUpdateTokenInfoDecoder` - This class provides methods and constants for decoding the given `HssCallAttempt` into a `ScheduleCreateTransactionBody`.

### Gas Costs

The gas costs will consist of the price for the call to the Schedule Service plus the pricing for the transaction that would be executed (for creation transactions this would include sending value with the call to cover the fees) and the intrinsic gas cost plus mark-up.

### Feature Flags

In order to gate the newly introduced system contract calls, we will introduce the following feature flags:
- `systemContract.scheduleService.systemContracts.enabled` - to enable the `scheduleCreateFungibleToken`, `scheduleCreateFungibleTokenWithCustomFees`, `scheduleCreateNonFungibleToken`, `scheduleCreateNonFungibleTokenWithCustomFees` and `scheduleUpdateTokenInfo` functions.

## Security Implications

The newly added flows will adopt the HAPI authorization logic and the security V2 model.
The throttles for `ScheduledCreate` and `ScheduledGetInfo` will be applied to the newly added functions.

## Phased Implementation

1. Implement the `shceduleCreateFungibleToken`, `scheduleCreateNonFungibleToken` system contract functions.
2. Implement the `getScheduledFungibleTokenCreateTransaction`, `getScheduledNonFungibleTokenCreateTransaction` system contract functions.
3. Implement the `getScheduledTokenAddress` system contract function.
4. Implement the `scheduleUpdateTokenInfo` system contract function.
5. Implement the `scheduleCreateFungibleTokenWithCustomFees`, `scheduleCreateNonFungibleTokenWithCustomFees` system contract functions.

## Acceptance Tests

### BDD Tests

#### Positive Tests

- validate that `getScheduledFungibleTokenCreateTransaction` returns the correct token info for a given schedule address.
- validate that `getScheduledNonFungibleTokenCreateTransaction` returns the correct non-fungible token info for a given schedule address.
- validate that `getScheduledTokenAddress` returns the correct token address for a given schedule that executed successfully.
- validate that `scheduleCreateFungibleToken` successfully creates a schedule for create token and returns the schedule address.
- validate that `scheduleCreateFungibleTokenWithCustomFees` successfully creates a schedule for create token with custom fees and returns the schedule address.
- validate that `scheduleCreateNonFungibleToken` successfully creates a schedule for create nft and returns the schedule address.
- validate that `scheduleCreateNonFungibleTokenWithCustomFees` successfully creates a schedule for create nft with custom fees and returns the schedule address.
- validate that `scheduleUpdateTokenInfo` successfully creates a schedule for token update and returns the schedule address.
- validate that the gas cost is correctly calculated for the newly added functions.

#### Negative Tests

- validate that `getScheduledFungibleTokenCreateTransaction` returns an error for a non-existing schedule address.
- validate that `getScheduledNonFungibleTokenCreateTransaction` returns an error for a non-existing schedule address.
- validate that `getScheduledTokenAddress` returns an error for a non-existing schedule address.
- validate that the create/update functions would not be executed if the required signers did not sign the schedules.
