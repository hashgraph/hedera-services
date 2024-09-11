# Frictionless Airdrops via System Contracts

## Purpose

[HIP-904](https://hips.hedera.com/hip/hip-904) introduced the Frictionless Airdrops feature for fungible and non-fungible tokens.
This document will define the architecture and implementation of the `airdropToken`, `claimAirdrops`, `cancelAirdrops` and `rejectTokens` smart contract functions
and their respective redirect function calls (`cancelAirdropFT`, `cancleAirdropNFT`, `claimAirdropFT`, `claimAirdropNFT`, `rejectTokenFT`, `rejectTokensNFT`, `setAutomaticAssociations`) that will extend the capabilities of the Hedera Smart Contract Service (HSCS) to support frictionless airdrops.

## References

[HIP-904](https://hips.hedera.com/hip/hip-904) - HIP that introduces the frictionless airdrops.

## Goals

- Expose `airdropToken`, `claimAirdrops`, `cancelAirdrops` and `rejectTokens` as new functions in the Hedera Token Service Smart Contract.
- Expose `cancelAirdropFT`, `cancleAirdropNFT`, `claimAirdropFT`, `claimAirdropNFT`, `rejectTokenFt`, `rejectTokensNFT`, `setAutomaticAssociations` as new functions in the proxy redirect token facade contract IHRC.
- Implement the needed HTS system contract classes to support the new functions.

## Non Goals

- The implementation of the HAPI operation, as it is already an existing feature.

## Architecture

The architecture for the frictionless airdrops follows the existing framework defined for handling all calls to the HederaTokenService system contract in the modularization services and is described in more detail in the Implementation section below.

## Implementation

### New Solidity Structures

We will introduce the following new structures to support the new functionality:

`PendingAirdrop` - A struct that represents a pending airdrop request.

```solidity
struct PendingAirdrop {
    address sender;
    address receiver;

    address token;
    bool isNft;
    int64 serial;
}
```

`PendingAirdropRecord` - A struct that represents a pending airdrop record.

```solidity
struct PendingAirdropRecord {
    PendingAirdrop pendingAirdrop;
    uint64 amount;
}
```

`NftId` - A struct that represents the Nft serials to be rejected.

```solidity
struct NftId {
    address nft;
    int64[] serials;
}
```

### New Solidity Functions

New system contract functions must be added to the `IHederaTokenService` interface to support airdropping tokens.

| Function Selector Hash |                                                         Function Signature                                                          |                 Response                 |                                                                           |
|------------------------|-------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------|---------------------------------------------------------------------------|
| `0x2f348119`           | `function airdropTokens(TokenTransferList[] memory tokenTransfers) external returns (int64 responseCode)`                           | `ResponseCode`                           | The response code from the call                                           |
| `0xc1acfe5d`           | `function cancelAirdrops(PendingAirdrop[] memory pendingAirdrops) external returns (int64 responseCode)`                            | `ResponseCode`                           | The response code from the call                                           |
| `0x241b6af7`           | `function claimAirdrops(PendingAirdrop[] memory pendingAirdrops) external returns (int64 responseCode)`                             | `ResponseCode`                           | The response code from the call                                           |
| `0x012ea0b1`           | `function rejectTokens(address[] memory ftAddresses,  NftId[] memory nftIds) external returns (int64 responseCode)`                 | `ResponseCode`                           | The response code from the call                                           |
| `0xc60ffe1b`           | `function getPendingAirdrops(address account) external returns (int64 responseCode, PendingAirdropRecord[] memory pendingAirdrops)` | `ResponseCode`, `PendingAirdropRecord[]` | The response code from the call and the pending airdrops for given sender |

New system contract functions must be added to the `IHRC` interface to support airdropping tokens.

| Function Selector Hash |                                                Function Signature                                                |    Response    |                                 |
|------------------------|------------------------------------------------------------------------------------------------------------------|----------------|---------------------------------|
| `0xcef5b705`           | `function cancelAirdropFT(address receiverAddress) external returns (uint256 responseCode)`                      | `ResponseCode` | The response code from the call |
| `0xad4917cf`           | `function cancelAirdropNFT(address receiverAddress, int64 serialNumber) external returns (uint256 responseCode)` | `ResponseCode` | The response code from the call |
| `0xa83bc5b2`           | `function claimAirdropFT(address senderAddress) external returns (uint256 responseCode)`                         | `ResponseCode` | The response code from the call |
| `0x63ada5d7`           | `function claimAirdropNFT(address senderAddress, int64 serialNumber) external returns (uint256 responseCode)`    | `ResponseCode` | The response code from the call |
| `0x76c6b391`           | `function rejectTokenFT() external returns (uint256 responseCode)`                                               | `ResponseCode` | The response code from the call |
| `0xedd9c546`           | `function rejectTokensNFT(int64[] memory serialNumber) external returns (uint256 responseCode)`                  | `ResponseCode` | The response code from the call |
| `0x966884d4`           | `function setAutomaticAssociations(int64 newAutoAssociations) external returns (uint256 responseCode)`           | `ResponseCode` | The response code from the call |

### System Contract Module

- `AirdropTokensTranslator` - This class will be responsible for handling the `airdropTokens` selector and dispatching it to the corresponding HAPI calls.
- `AirdropTokensDecoder` - This class provides methods and constants for decoding the given `HtsCallAttempt` into a `TokenTransferList` for `TokenAirdrop` call.
- `CancelAirdropsTranslator` - This class will be responsible for handling the `cancelAirdrops`, `cancelAirdropFT` and `cancelAirdropNFT` selectors and dispatching them to the corresponding HAPI calls.
- `CancelAirdropsDecoder` - This class provides methods and constants for decoding the given `HtsCallAttempt` into a `PendingAirdropId` list for `TokenCancelAirdrop` call.
- `ClaimAirdropsTranslator` - This class will be responsible for handling the `claimAirdrops`, `claimAirdropFT` and `claimAirdropNFT` selectors and dispatching them to the corresponding HAPI calls.
- `ClaimAirdropsDecoder` - This class provides methods and constants for decoding the given `HtsCallAttempt` into a `PendingAirdropId` list for `TokenClaimAirdrop` call.
- `RejectTokensTranslator` - This class will be responsible for handling the `rejectTokens`, `rejectTokenFT` and `rejectTokenNFT` selectors and dispatching them to the corresponding HAPI calls.
- `RejectTokensDecoder` - This class provides methods and constants for decoding the given `HtsCallAttempt` into `TokenReference` list for `TokenReject` call.
- `GetPendingAirdropsTranslator` - This class will be responsible for handling the `getPendingAirdrops` selector for querying the pending airdrop store.
- `GetPendingAirdropsCall` - This class will be responsible for encoding and dispatching the response data.

## Security Implications

The newly added flows will adopt the HAPI authorization logic and the security V2 model.
We will apply the `TokenReject`, `TokenAirdrop`, `TokenClaimAirdrop`, `TokenCancelAirdrop` throttle mechanisms.

## Acceptance Tests

### BDD Tests

#### Positive Tests

- Verify that the `airdropTokens` function airdrops multiple tokens bot ft and nft to multiple accounts.
- Verify that the `airdropTokens` function airdrops a fungible token to an account.
- Verify that the `airdropTokens` function airdrops a nft token to an account.
- Verify that the `cancelAirdrops` function cancels multiple pending airdrops.
- Verify that the `cancelAirdrops` function cancels single pending airdrop.
- Verify that the `claimAirdrops` function claims multiple pending airdrops.
- Verify that the `claimAirdrops` function claims a single pending airdrop.
- Verify that the `rejectTokens` function rejects tokens for multiple accounts.
- Verify that the `getPendingAirdrops` function returns the pending airdrops for a given account.
- Verify that the `cancelAirdropFT` function cancels a pending airdrop of the redirected token.
- Verify that the `cancelAirdropNFT` function cancels a pending airdrop of the redirected nft serial.
- Verify that the `claimAirdropFT` function claims a pending airdrop of the redirected token.
- Verify that the `claimAirdropNFT` function claims a pending airdrop of the redirected nft serial number.
- Verify that the `rejectTokenFT` function rjects tokens for a given account.
- Verify that the `rejectTokensNFT` function rejects tokens for a given account and serial number.
- Verify that the `setAutomaticAssociations` function sets the automatic associations for a given account.
- Verify that the `setAutomaticAssociations` function sets the automatic associations to unlimited (-1) for a given account.
- Verify that the `setAutomaticAssociations` function sets the automatic associations to zero for a given account.

#### Negative Tests

- Verify that the `airdropTokens` function fails when the sender does not have enough balance.
- Verify that the `airdropTokens` function fails when the receiver does not have a valid account.
- Verify that the `airdropTokens` function fails when the token does not exist.
- Verify that the `cancelAirdrops` function fails when the sender does not have any pending airdrops.
- Verify that the `cancelAirdrops` function fails when the sender does not have a valid account.
- Verify that the `cancelAirdrops` function fails when the receiver does not have a valid account.
- Verify that the `cancelAirdrops` function fails when the token does not exist.
- Verify that the `cancelAirdrops` function fails when the nft does not exist.
- Verify that the `cancelAirdrops` function fails when the nft serial number does not exist.
- Verify that the `claimAirdrops` function fails when the sender does not have any pending airdrops.
- Verify that the `claimAirdrops` function fails when the sender does not have a valid account.
- Verify that the `claimAirdrops` function fails when the receiver does not have a valid account.
- Verify that the `claimAirdrops` function fails when the token does not exist.
- Verify that the `claimAirdrops` function fails when the nft does not exist.
- Verify that the `claimAirdrops` function fails when the nft serial number does not exist.
- Verify that the `rejectTokens` function fails when the sender does not have any associated tokens.
- Verify that the `rejectTokens` function fails when the sender does not have a pending airdrop.
- Verify that the `rejectTokens` function fails when the provided fungible token is invalid.
- Verify that the `rejectTokens` function fails when the provided nft is invalid.
- Verify that the `getPendingAirdrops` function fails when the sender does not have a valid account.
- Verify that the `cancelAirdropFT` function fails when the sender does not have any pending airdrops.
- Verify that the `cancelAirdropFT` function fails when the sender does not have a valid account.
- Verify that the `cancelAirdropNFT` function fails when the sender does not have any pending airdrops.
- Verify that the `cancelAirdropNFT` function fails when the sender does not have a valid account.
- Verify that the `claimAirdropFT` function fails when the sender does not have any pending airdrops.
- Verify that the `claimAirdropFT` function fails when the sender does not have a valid account.
- Verify that the `claimAirdropNFT` function fails when the sender does not have any pending airdrops.
- Verify that the `claimAirdropNFT` function fails when the sender does not have a valid account.
- Verify that the `rejectTokenFT` function fails when the sender does not have any tokens.
- Verify that the `rejectTokenFT` function fails when the sender does not have a valid account.
- Verify that the `rejectTokensNFT` function fails when the sender does not have any tokens.
- Verify that the `setAutomaticAssociations` function fails when the provided value is less than -1.
