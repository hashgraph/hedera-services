# Atomic Crypto Transfer

## Purpose

[HIP-206](https://hips.hedera.com/hip/hip-206) defines a way to perform atomic crypto transfers between account using the IHederaTokenService interface.  This allows smart contract
developers to perform atomic transfers of HTS tokens and hbars between accounts.  This document will define the architecture and implementation of this functionality.


## Goals

Describe the implementation of the atomic crypto transfer functionality in the Hedera Smart Contract Service.  Specifically, only the implementation in the modularized code will be described in this document.

## Non Goals

- The implementation of the atomic crypto transfer functionality in the mono service codebase will not be described in this document.
- The implementation details on `allowance` and `approvals` for hbar from within smart contracts is outside the scope of this document and will be described in a separate document.

## Architecture

The architecture for atomic crypto transfer follows the framework defined for handling all calls to the HederaTokenService system contract and described in more detail in the `Implementation` section below.

## Implementation
The following classes are used to handle the call to the atomic crypto transfer function:

### Hedera Smart Contract Service
1.  The smart contract service implements a `CustomMessageCallProcessor` class which overrides the Besu `MessageCallProcessor` class in order to potentially intercept calls to system contract addresses.
If the contract address of the current call is determined to be the contract address for the HederaTokenService (0x167), the call is redirected to the `HtsSystemContract` class for processing. 
2.  The `HtsSystemContracts` creates an instance of the `HtsCallAttempt` class from the input bytes and the current message frame to encapusulate the call.
3.  The `HtsCallAttempt` class iterates through a list of `Translator` (provided by Dagger) classes in order to determine which translator will be responsible for processing the call by attempting to match the call's 
function signature to a signature known by the translator.  Specifically, the `ClassicTransferTranslator` class will be responsible for processing the atomic transfer function calls for atomic crypto transfer with signature: \
```cryptoTransfer(((address,int64,bool)[]),(address,(address,int64,bool)[],(address,address,int64,bool)[])[])```
4.  The `ClassicTransferTranslator` class will call the `ClassicTransferDecoder` class to decode the parameters of the call and after decoding translate the encoded parameter into a `TransactionBody` object.
5.  The `ClassicTransferCall` then takes the created `TransactionBody` object and dispatches a new transaction to the Hedera Token Service for processing.  It is also responsible
for checking for sufficient gas and encoding the response.

### Hedera Token Service

Once the smart contract service dispatches the transaction to the Hedera Token Service it performs the following steps:

1. Validate semantic correctness of the transaction.
2. Handle any aliases found and potentially create hollow accounts as necessary.
3. Handle auto associates
4. Handle custom fees and add the resulting transfers to the transfer list
5. Perform the transfers between accounts
6. Create the record and return to the caller. 

Most of the implementation can be found int the `CryptoTransfeHandler` class.

## Acceptance Tests

As outlined above most of the implementation of the atomic crypto transfer functionality has already been completed.  What remains is to write acceptance tests
to validate the functionality with a particular emphasis on security and edge cases and fixing issues as they arise.

### XTests

#### Positive Tests

#### Negative Tests
- Failure when amounts transferred do not sum to zero.
- Failure if sender attempts to transfer more hbars/tokens than they have.
- Failure when sender does not contain sufficient hbar/tokens to pay custom fees.
- Failure when receiver does not have sufficient auto association slots to receive token(s).
- Failure in case of insufficient approval from EOA and contract accounts.

### BDD Tests

BDD tests are required to cover security concerns which require complex signing scenarios.  Many of these tests
are already implemented and need not be repeated as XTests.

#### Positive Tests
- Successful transfer of hbars and HTS tokens from sender account.
- Successful transfer of hbars only and HTS tokens only between accounts from sender account.
- Successful transfer of hbars and HTS tokens with a custom fees (including fallback fee scenarios).
- Successful transfer of hbars and HTS tokens with available auto token association slots on the receiver.
- Successful transfer of hbars and HTS tokens from EOA account given approval.
- Successful transfer of hbars and HTS tokens from contract given approval.

#### Negative Tests

- Failure when attempting to transfer from special system accounts.
- Failure when receiver signature is required and not provided.
