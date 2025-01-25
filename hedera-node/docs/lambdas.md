---

hip: <HIP number (assigned by the HIP editor)>
title: Hiero lambdas and an application to allowances
author: Michael Tinker <@tinker-michaelj>
working-group: Atul Mahamuni <atul@hashgraph.com>, Richard Bair <@rbair23>, Jasper Potts <@jasperpotts>, Matthew DeLorenzo <@littletarzan>
requested-by: Anonymous
type: Standards Track
category: Service
needs-council-approval: Yes
status: Draft
created: 2025-01-25
discussions-to: <TODO>
updated: 2025-01-25
-------------------

## Abstract

We propose Hiero **lambdas**, lightweight EVM functions that users can **install** to extend and customize the native
protocol. Each lambda is owned by a single entity, making updates fast and cheap without compromising protocol
integrity. Once installed to an entity $E$, any transaction interacting with $E$ may reference its lambdas to apply
custom behavior.

The **type** of a lambda determines where it can be installed, which transactions can reference it, and exactly how the
protocol applies its logic. For example, a `PRE_FUNGIBLE_CREDIT` lambda can be installed on an account, referenced by a
`CryptoTransfer` transaction; and the protocol will execute it to decide whether a fungible Hedera Token Service (HTS)
asset may be credited to the account. Lambdas use EVM **application binary interfaces (ABI)**, ensuring a clear
contract between the protocol and user-defined logic.

Unlike standard smart contracts, which must encapsulate their own trust guarantees for multiple parties, Hiero
lambdas belong to a single owner who can directly update their storage via a native `LambdaSStore` transaction.
This streamlined design enables fast, low-cost adjustments to a lambda’s logic and state without the overhead of
contract calls.

As a first application, we introduce a family of **allowance** lambdas installable on Hiero accounts and
referenceable by `CryptoTransfer`. These lambdas allow customizations such as requiring receiver signatures
only for HTS (Hedera Token Service) tokens, or creating one-time credit allowances gated by a shared secret that
must be set in the `memo` field.

## Motivation

Hedera users frequently seek to customize native entities instead of migrating their decentralized applications (dApps)
to purely EVM-based smart contracts. We see this in multiple proposals:
- [HIP-18: Custom Hedera Token Service Fees](https://hips.hedera.com/hip/hip-18) introduced custom fee
payments for HTS transfers.
- [HIP-904: Frictionless Airdrops](https://hips.hedera.com/hip/hip-904) enabled more permissive token association policies.
- The in-progress [HIP-991: Permissionless revenue-generating Topic Ids for Topic Operators](https://hips.hedera.com/hip/hip-991)
proposes fee-based access control for message submissions to topics.

In principle, these sorts of enhancements could be written as smart contracts, _if_ the protocol exposed suitable
"hooks" to inject custom logic. But without these hooks, users must either switch to a more EVM-centric architecture
or undertake the slow, complex process of designing, drafting, and building consensus around a new HIP.

We believe lambdas fill this gap by providing carefully chosen extension points within the native protocol. With
lambdas, the use cases motivating HIP-18, HIP-904, and HIP-991---along with many other past and future enhancements---
could be realized at the protocol layer with less complexity and a broader feature set. By avoiding new protocol-level
changes for every customization, lambdas can greatly streamline innovation while maintaining the performance and
integrity of Hedera’s native services.

## Specification

Next we outline how lambdas interact with a Hiero network in terms of charging, throttling, and execution environment.
The detailed lambda protobuf API follows.

### Charging

A primary concern for lambdas is determining account pays for their EVM gas. We propose two charging patterns that
should accommodate most use cases.
1. `CALLER_PAYS` - The payer of the transaction that references the lambda is charged for all used gas. They only
receive the normal refund for unused gas.
2. `CALLER_PAYS_ON_REVERT` - The referencing transaction's payer is initially charged, but receives a _full refund_
if the lambda does not revert. In that successful scenario, a designated account that authorized the lambda's
installation pays for the gas actually consumed.

Regardless of the charging pattern, the referencing transaction can impose an explicit gas limit for the lambda's
execution. If no explicit limit is set on the transaction, the protocol checks if the lambda was installed with a
default gas limit. If neither of those limits is specified, the protocol uses a global property, for example
`lambdas.defaultGasLimit=25_000`.

We propose the same gas price for lambda execution as for other contract operations. (Implementations could
optionally reduce the intrinsic gas cost of the lambda's execution by the fee already charged for the native
transaction referencing the lambda.

### Throttling

We propose that lambdas be subject to the same gas throttle as top-level contract calls. Specifically, when a lambda
executes, its initial EVM sender address is the payer of the referencing transaction. If this payer is a system account,
no throttles are applied. Otherwise, if the network is at capacity for gas usage, lambda execution can be throttled on
that basis and the referencing transaction will roll back with final status of `LAMBDA_GAS_EXHAUSTED`.

### Lambda execution environment

Although a lambda does exists in the network state with its own bytecode and storage, it does **not** have a directly
callable EVM address. You cannot invoke it through the Hedera API (HAPI) or reference it from other smart contracts by
address.

Instead, when a lambda is executed, both the EVM receiver and contract address in the initial frame are set to a new
system contract address, `0x16c`. This system contract implements a new `IHieroTransactionEnv` interface that exposes
the context of the parent Hiero transaction. While HAPI contract calls can also invoke `0x16c` directly, it exists
primarily to support lambdas. In a lambda's code, the interface methods can be called via shorthand; for example,
`IHieroTransactionEnv(this).memo()`, because every executing lambda has `0x16c` address.

Below is the proposed `IHieroTransactionEnv` interface.

```solidity
// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.4.9 <0.9.0;
pragma experimental ABIEncoderV2;

/// Provides context about the Hiero transaction initiating the EVM transaction.
interface IHieroTransactionEnv {
    /// Returns if the account at the given address had an active signature on
    /// the parent Hiero transaction.
    /// @param account The address of an account
    /// @return verdict Whether the account signed the parent transaction
    function isSigner(address account)
    external
    returns (bool verdict);

    /// Returns the memo of the parent Hiero transaction.
    /// @return memo The memo of the parent transaction
    function memo()
    external
    returns (string memory memo);

    /// Returns the asset transfers proposed by the parent Hiero transaction,
    /// including any assessed custom fees. (Network fee payments are excluded.)
    /// @return hbarTransfers Proposed HBAR balance changes
    /// @return tokenTransfers Proposed NFT owner and fungible token balance changes
    function assetTransfers()
    external
    returns (
        TransferList memory hbarTransfers,
        TokenTransferList[] memory tokenTransfers
    );
}
```

In summary, the system contract address and interface means lambdas can access key transaction context---such as
signers, memos, and proposed asset transfers---while remaining isolated from external contract calls unless explicitly
referenced by a native Hiero transaction.
