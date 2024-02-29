# Cancun Fork Support

## Purpose

The Ethereum "Cancun" hardfork introduces a number of changes to the EVM that will need to be implemented to maintain EVM
Equivalence. There are four different HIPs covering this feature (tracked in  
  epic [#11697](https://github.com/hashgraph/hedera-services/issues/11697):
* [HIP-865](https://hips.hedera.com/hip/hip-865): Add EVM Support for transient storage and memory 
  copy Cancun opcodes (issue [#11699](https://github.com/hashgraph/hedera-services/issues/11699))
* [HIP-866](https://hips.hedera.com/hip/hip-866): Add EVM compatibility for non-supported Cancun blob features
  (issue [#11700](https://github.com/hashgraph/hedera-services/issues/11700))
* [HIP-867](https://hips.hedera.com/hip/hip-867): Add Smart Contract Services Support for KZG Point 
  Evaluation Precompiled Function (issue [#11701](https://github.com/hashgraph/hedera-services/issues/11701))
* [HIP-868](https://hips.hedera.com/hip/hip-868): Support Cancun Self-Destruct  Semantics in Smart
  Contract Services (issue [#11702](https://github.com/hashgraph/hedera-services/issues/11702))

This document describes how the HIPs will be implemented in Hedera Services.

Generally speaking there are four strategies that will be adopted:

1. Adopt without change. This includes the `MCOPY`, `TSTORE`, and `TLOAD` operations as well as the KZG precompile.
2. Adapt with Hedera Considerations. This includes the `SELFDESTRUCT` changes.
3. Do not adopt but stub out placeholders. This includes the `VERSIONEDHASH` and `BLOBBASEFEE` operations.
4. Do not adopt. This includes all Blob handling and the "Type 3" blob transaction.

### Example User Story

<!-- **TODO(Nana): more specific user stories highlighting the capabilities to users** -->

* As a smart contract developer, I want to use current versions of solidity that may generate opcodes that are only
  available in the Cancun fork.
* As a Hedera developer, I want to preserve maximum future design space to adopt, adopt, or not adopt blobs.
* As an end user, I want prompt and accurate failures if I attempt to use Blob features in Hedera.

## Goals

The principal goal is to maintain EVM and Solidity/Vyper/etc. compatibility, where the bytecode from contracts compiled
with Cancun enabled smart contract compilers still function correctly.

Where inclusion of features don't require additional work, they too should be included.

Where we have existing overrides, opcodes that change those behaviors should still operate in a fashion that is in
harmony with the EVM equivalence requirements and the Hedera requirements to the extent possible. Working with Hedera
takes priority when there is unavoidable conflict.

Opcodes that interact with blobs should not cause the contracts to break, but should not require blobs to work.

## Non Goals

It is not a goal to introduce blobs into Hedera. It is also not a goal to restrict design space nor to dictate future
design directions for support or non-support of blobs.

## Implementation

### New Cancun EVM

A new EVM version will be created that will be based off of the Cancun EVM - which is from a Besu
release `>24.1.2`.  A new enum value, `HederaEvmVersion.VERSION_049` will have the value `v0.49`.  

Setting the `@ConfigProperty` `ContractsConfig.evmVersion` (string value `contracts.evm.version`) 
to `v0.49` (from `v0.46`) will activate the Cancun EVM.

Simply activating the Cancun EVM - with no changes or overrides - will immediately provide the 
following features:

* `MCOPY` operation
* `TLOAD` and `TSTORE` operations
* `VERSIONEDHASH` and `BLOBBASEFEE` operations that query the `TxValues` Object
* KZG Precompiled contract

### Set Correct Values in TxValues

There are two new fields in the `TxValues` object that need to be set correctly. We need to ensure
that `versionedHashes` is set to an empty list and that `blobGasPrice` is set to one. We should be able to do that in all
EVM versions so setting them unconditionally in the `MessageFrame` construction in `HederaEvmTxProcessor` should be
sufficient.

<!-- **TODO(Nana): Set them in the EVM versions how? Is there a specific class that needs to be updated or a method that needs to be overridden?** -->


### Update HederaSelfDestructOperation behavior

Either with a new class or a class that takes a constructor parameter, update the HederaSelfDestructOperation so that it
will behave consistently with the EIP-6780 `SELFDESTRUCT` rules.

<!-- **TODO(Nana): Please specify which option the implementation should follow. Feel free to not that the other wasn't chosen for reason X** -->

## Acceptance Tests

Verify the presence of the following features in the new EVM via new smart contracts exercising their features:

* `MCOPY` operation
* `TLOAD` and `TSTORE` operations
* KZG Precompiled contract

Verify the presence of the following features in their "no blobs" state:

* `VERSIONEDHASH` will always return zero
* `BLOBBASEFEE` will always return 1

Verify Type 3 transactions produce errors when passed into EthereumTransaction:

* A well-formed "Type 3" blob transaction (without blob but with a versioned hash)
* A poorly formed "Type 3" transaction with valid RLP but invalid in some fashion
* A poorly formed "Type 3" transaction with the first byte `0x03` but the remainder is invalid RLP

Verify that the new Hedera Self Destruct operation behaves correctly:

* Verify that a self-destruct of a contract created in the same transaction deletes the contract
* Verify that a self-destruct of a contract not created in the same transaction leaves the contract in place
* For both of the above, verify that the HTS tokens are correctly sent to their beneficiary
