
# Hedera Account Service

## Purpose

In order to provider for a better developer experience when using the functionality made available in 
HIP 631, create a new Hedera Account Service which facilitates common useful functions 
for address translation and validation.

## Goals

Allow users using the Hedera Account Service to easily:
- Retrieve associated virtual evmAddresses given a Hedera Account Id.
- Retrieve the Hedera Account Id given a virtual evmAddress if one exists.
- Given a virtual evmAddress, determine if it is associated with a Hedera Account Id.
- Validate message hashes and signatureBlobs to ensure authorization.

## Non Goals
Currently, there is no planned functionality to facilitate the creation of signatureBlobs used for verification.
The need for such a facility can be re-evaluated in the future based on user feedback.

## Assumptions
It is assumed that a new `evmAddress → accountId` map as described in HIP 631 is implemented for constant time lookup of 
Hedera account Ids that map to evm addresses.  The reverse lookup `accountId → evmAddress` will be part of the protobuf record.

For testing purposes, it is assumed that special Hedera addresses (e.g. 0.0.800, 0.0.801) will be excluded from having virtual addresses.
Also, there will be a list of well known ethereum addresses that will not be allowed to become virtual addresses.

## Architecture
Create IHederaAccountService.sol and HederaAccountService.sol interface and contracts to define function signatures
as enumerated below.

| hash | signature | return                       | description |
| --- | --- |------------------------------| --- |
|e9482d42| getVirtualAddresses(address) | address[]                    | returns an array of virtual addresses for a given Hedera Account ID  |
|a4e310ba| getHederaAddress(address) | (responseCode, address) | returns the top level Hedera Account ID if applicable |
|d501235a| isVirtualAddress(address) | bool                         | true if valid virtual address, false if long-zero or non existing account |
|b2526367| isAuthorized(address, messageHash, signatureBlob) | bool                         | true if account is authorized to carry out transaction execution on account. Accepts protobuf key signature blobs. May be used for ECDSA, ED25519 and complex key flows |
|d501235a| isAuthorizedRaw(address, messageHash, signatureBlob) | bool                         | true if account is authorized to carry out transaction execution on account. Accepts single key raw signature blobs (ECDSA and ED25519). This provides similar logic to ECRECOVER. |

```isAuthorizedRaw``` may accept either a Hedera account Id or an evmAddress.  If the user passes a Hedera account Id, the
signature of all associated virtual addresses will be checked for a match.  If the user passes an evnAddress, only the given address
will be checked.

```isAuthorized``` accepts a signatureBlob which is a protobuf encoded ```SignatureMap``` as defined in following protobuf [schema](https://github.com/hashgraph/hedera-protobufs/blob/main/services/basic_types.proto#L783)

### New Classes

| class                       | description                                                                                                                                                                                                                                                                                                                                                             |
|-----------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| HASPrecompileContract       | Similar to HTSPrecompileContract.  Will be deployed at address `0x16a`                                                                                                                                                                                                                                                                                                  |
| AddressTranslatePrecompile  | Handle calls to ```getVirtualAddresses, getHederaAddress and isVirtualAddress```.  This class will make use of the maps created during the implementation of HIP 631 to translate Hedera account Id and evmAddresses.                                                                                                                                                   |
| AddressAuthorizedPrecompile | Handle calls to ```isAuthorized and isAuthorizedRaw```.  This class will make use of the `ECRECOVER` precompile in Besu or the signature validation functionality in the platform layer in order to determine the results.  Currently,the platform layer signature verification is accessed via `platform.getContext().getCryptography()::verifySync` but this is subject to change. |


## Acceptance Tests

### Positive Tests
* Verify ```getVirtualAddresses``` returns the correct list of evmAddresses when an account holds 0, 1 and multiple virtual addresses.
* Verify ``` getHederaAddress``` returns the correct Hedera account Id given a virtual evmAddress.
* Verify ``` isVirtualAddress``` correctly returns true when a given evmAddress is a virtual address. 
* Verify ``` isAuthorized``` correctly returns true when the signatureBlob has been validated to have been signed by a single key of a given address in the single key scenario. 
* Verify ``` isAuthorized``` correctly returns true when the signatureBlob has been validated to have been signed by the multiple keys of a given address in multisig scenarios (e.g. 3 of 3 keys).
* Verify ``` isAuthorized``` correctly returns true when the signatureBlob has been validated to have been signed by the sufficient multiple keys of a given address in threshold keys scenarios (e.g. 2 of 3 keys).
* Verify ``` isAuthorizedRaw``` correctly returns true when the signatureBlob has been validated to have been signed by the evm key of a given address.


### Negative Tests
* Verify ```getVirtualAddresses``` returns an empty list of evmAddresses given an unknown Hedera account Id.
* Verify ```getVirtualAddresses``` returns INVALID_ACCOUNT_ID given a `special` Hedera account Id (e.g. 0.0.800, 0.0.801).
* Verify ``` getHederaAddress``` returns INVALID_ACCOUNT_ID given a non-existing virtual evmAddress.
* Verify ``` getHederaAddress``` returns INVALID_ACCOUNT_ID given an evmAddress that is on the list of well known evm addresses.
* Verify ``` isVirtualAddress``` correctly returns false when a given evmAddress is not a virtual address.
* Verify ``` isAuthorized``` correctly returns false when the signatureBlob does not validate to have been signed by the key of a given address in the single key scenario. Also validate multikey/nested scenarios.
* Verify ``` isAuthorized``` correctly returns false when the signatureBlob does not validate to have been signed by the keys of a given address in the multisig scenarios. 
* Verify ``` isAuthorized``` correctly returns false when the signatureBlob does not validate to have been signed by the sufficient keys of a given address in the threshold key scenario.
* Verify ``` isAuthorized``` returns error when passed signatureBlob in incorrect format (not encoded SignatureMap)
* Verify ``` isAuthorizedRaw``` correctly returns false when the signatureBlob does not validate to have been signed by the evm key of a given address.


