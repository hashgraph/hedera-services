# EVM Versioning

## Purpose

Provide a code infrastructure to enable multiple "versions" or configurations of the EVM, and to enable switching
between these major versions via configuration parameters instead of code changes.

The Ethereum Virtual Machine is regularly updated on Ethereum Mainnet via a series of "Forks" where the behavior of the
EVM changes. These can range from gas schedule changes to new operations to new data formats for the EVM. One major
feature of each fork is that there is a well established impact on backwards compatibility that is only compromised when
security demands it.

In order for off-chain evaluation of these prior EVMs to work we also need to preserve a way to re-create the EVM
classes in such a way so that the other instances can re-create the execution.

## Goals

- Allow EVM "versions" to be switched by dynamic system properties
- Allow EVM infrastructure to be committed to production code without requiring it to be activated at consensus.
- Allow older versions of the EVM to remain accessible for

## Non Goals

- Switching the EVM version dynamically at runtime. EVM will be locked in at system startup

## Architecture

First, the EVM object will be created by dagger injection. Previously these were constructed in the EVM Processor for
each HederaTransaction Type, where Dagger provided the operations and gas calculator and the EVM was created in situ.

Second, the dagger injection will be done via a map of providers, keyed by a text string corresponding to the advertised
version of the EVM. Using a provider will result in just-in-time construction of the object and will not cause multiple
versions of the EVM to exist in-memory.  (Done via `@IntoMap` bindings for the EVM and injected
as `Map&lt;String, Provider&lt;EVM>>`).

Finally, the required differences will be populated into separate submodules, using a version appropriate dagger
qualifier.

## Non-Functional Requirements

A standard dynamic property will be used to configure the EVM at startup (`contracts.evm.version`) and will be defaulted
to the last activated version on mainnet in the event the value is not set.

EVM versions will follow the format `v&lt;major>.&lt;minor>`, corresponding to the released version of hedera. for
example, the earliest version supported by this regime is `v0.30` with a planned update for `v0.32`. Not every hedera
version will have a new EVM version. Only when EVM compatability is impacted for object replay will a new version be
set.

It is expected that there will be a new version for each major Ethereum Mainnet hard fork. A table will be kept here to
document which major hardfork corresponds to each internal version.

| Hedera Version | Ethereum Fork                                                                                                     | Comments                                                                              |
|---------------:|:------------------------------------------------------------------------------------------------------------------|:--------------------------------------------------------------------------------------|
|        `v0.30` | [London](https://github.com/ethereum/execution-specs/blob/master/network-upgrades/mainnet-upgrades/london.md)     |                                                                                       |
|        `v0.32` | [Paris](https://github.com/ethereum/execution-specs/blob/master/network-upgrades/mainnet-upgrades/paris.md)       | Replaces `DIFFICULTY` with `RANDAO`, removes errors from Invalid Solidity Addresses   |
|              ? | [Shanghai](https://github.com/ethereum/execution-specs/blob/master/network-upgrades/mainnet-upgrades/shanghai.md) | Expected Q2 2023 or later                                                             |

## Open Questions

The exact timing of versions that correspond to Ethereum Mainnet forks is out of scope of this document

## Acceptance Tests

Acceptance tests use the Paris re-definition of `DIFFICULTY` as the test for activation
* Test that when dynamic is set to false that changing the version has no effect
* Test when dynamic is set to true the evm version can change at each transaction
* verify 0.30 still returns zeros for difficulty
* verify 0.32 returns prng values
* verify that prng behaviors are reflected in the opcode (use same tests as PRNG contract)
