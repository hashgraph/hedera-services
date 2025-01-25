---

hip: <HIP number (assigned by the HIP editor)>
title: Hiero lambdas and an application to allowances
author: Michael Tinker <@tinker-michaelj>
working-group: Atul Mahamuni <atul@hashgraph.com>, Richard Bair <@rbair23>, Jasper Potts <@jasperpotts>
requested-by: Anonymous
type: Standards Track
category: Service
needs-council-approval: Yes
status: Draft
created: 2025-01-21
discussions-to: <TODO>
updated: 2025-01-21
-------------------

## Abstract

We propose Hiero **lambdas**, EVM functions run as "hooks" within the Hiero native protocol. Users **attach** lambdas
to entities, to be either referenced by transactions (as **user** lambdas) or triggered by events (as **system**
lambdas).

The **type** of a lambda determines where it can be used. For example, a lambda of type `PRE_FUNGIBLE_CREDIT` can be
attached to an account as a system lambda; the protocol will then consult it every time it is about to credit fungible
tokens to the account. The same EVM bytecode could be also be attached as a user lambda; then it will be called only
when explicitly referenced by a `AccountAmount` credit in a fungible `TokenTransferList` of a `CryptoTransfer`
transaction.

An entity can have at most one attached system lambda of a given type, but may attach multiple user lambdas of
all the same type.

**Public** lambdas can be attached to multiple entities, and hence cannot be mutated via the Hedera gRPC API (HAPI).
**Private** lambdas are attached to a single entity and can be mutated via HAPI by the entity owner.

We do not try to survey all interesting lambda types in this HIP, as there will be a great diversity of such
applications. We propose only a family of **allowance lambda** types that are illustrative and useful. We give
three examples of using allowance lambdas to,
- Implement a `receiver_sig_required` variant that applies to HTS tokens but not HBAR.
- Create a one-time credit allowance that requires use of a shared secret in the `memo` field.
- Trigger a contract call after an HBAR credit happening `CryptoTransfer`.

Note that we only propose triggering lambdas inside HAPI transactions that are not already contract executions, as
the main goal is to customize the native protocol. Contract behavior already has arbitrary flexibility.

## Motivation

Hedera users have often wanted to customize the network protocol for native assets and entities. For example,
[HIP-18: Custom Hedera Token Service Fees](https://hips.hedera.com/hip/hip-18) added a way to require custom fee
payments as part of HTS transfers. [HIP-904: Frictionless Airdrops](https://hips.hedera.com/hip/hip-904) gave native
support to more permissive token association policies. The in-progress
[HIP-991: Permissionless revenue-generating Topic Ids for Topic Operators](https://hips.hedera.com/hip/hip-991)
proposes fee-based access control to topic message submission.

Users could easily express such customizations in the form of a Solidity smart contract---if there were appropriate
"hooks" to inject the custom logic into the native protocol. Without such hooks, users must either switch to a more
EVM-centric dApp; or go through the slow and complex process of designing, writing, and building consensus on a HIP.

We believe that with thoughtfully chosen extension points, lambdas could have met the needs that inspired HIP-18,
HIP-904, HIP-991, and many more past and future HIPs. The implementation at the native layer would often have been
simpler; and the unlocked feature set would be even more flexible and powerful than the protocol changes ultimately
encoded by HIPs like these.

## Rationale

There are two main reasons to begin with EVM bytecode as the lambda format. Namely,
1. Most Web3 developers can start writing lambdas immediately, as they are already familiar with Solidity and the EVM.
2. The Hedera EVM runtime already supports extensive interactions with native entities via system contracts.

We distinguish between system and user lambdas because we see a material difference between these types of lambda
triggers. When a user wants to customize how their account responds to a request for rent payment on some file, they
need to be able to attach a lambda the _system_ can efficiently identify and use. Similarly, if a user wants to
customize the authorization model for their account, they will again need the network to _systematically_ consult their
lambda for every transaction touching their account. On the other hand, if a user only wants a allowance to facilitate
a certain type of transfer, there is no reason to attach the lambda to their account in a way particularly to the
protocol. It only needs to be possible for another _user_ to reference the lambda from each relevant transfer.

The public versus private distinction is a nod to the fact that some lambdas could be helpful to many users. (Although
most lambdas, especially stateful ones, will presumably be relevant only to a single user.)

## User stories

1. As an account owner that wants custom receiver signature requirements, I can attach system lambdas of type
   `PRE_FUNGIBLE_CREDIT` and `PRE_NFT_RECEIPT` to my account so that all non-HBAR deposit attempts automatically check if
   the transaction was signed by a trusted address.

2. As an account owner who has enabled the native `receiver_sig_required` flag, I can attach a `PRE_HBAR_CREDIT` user
   lambda to give one-time permission to a counterparty to send me HBAR by using a shared secret as the memo of their
   `CryptoTransfer`.

3. As a NFT treasury account owner, I can attach a `POST_HBAR_RECEIPT` system lambda to the treasury so it automatically
   run a fallback function every time it receives a fungible token transfer.

## Specification

### Charging and throttling

Users configure the how the `gas` payer will be determined for lambda calls at the time they attach the lambda to an
entity. The payer may either be set explicitly in the `LambdaAttach` transaction (assuming its signing requirements
are met); or, when attaching to an account or contract, left unset. We expect the unset case to be most common.

The default payer in the unset case depends on whether the lambda is a system or user lambda.
- For a system lambda, the account paying to attach the lambda will pay for the lambda's gas each time it is triggered.
- For a user lambda, the payer of the transaction triggering the lambda will pay for the lambda's gas.

Whenever a lambda is executed, the sender address on the initial EVM frame is the `gas` payer.

Lambda executions are subject to the same throttles as normal contract executions.

### Core HAPI

The type of a lambda is one of an enumeration,

```protobuf
enum LambdaType {
  PRE_HBAR_CREDIT = 0;
  POST_HBAR_CREDIT = 1;
  PRE_FUNGIBLE_CREDIT = 2;
  POST_FUNGIBLE_CREDIT = 3;
  PRE_NFT_RECEIPT = 4;
  POST_NFT_RECEIPT = 5;
  PRE_HBAR_DEBIT = 6;
  POST_HBAR_DEBIT = 7;
  PRE_FUNGIBLE_DEBIT = 8;
  POST_FUNGIBLE_DEBIT = 9;
  PRE_NFT_SEND = 10;
  POST_NFT_SEND = 11;
}
```

The names are self-explanatory, but correspond to Solidity function signatures as below,

```solidity
pragma solidity ^0.8.0;

interface IAllowanceLambdas {
    /**
     * Invoked just before HBAR is credited to the entity that owns this lambda.
     * Return `true` to permit the credit, or `false` (or revert) to deny it.
     */
    function preHbarCredit(
        address from,
        int64 amount
    ) external returns (bool authorized);

    /**
     * Invoked just after HBAR is credited to the entity that owns this lambda.
     * May revert to roll back the transfer if needed, or just do side-effects.
     */
    function postHbarCredit(
        address from,
        int64 amount
    ) external;

    /**
     * Invoked just before a fungible token is credited to the entity.
     * Return `true` to permit the credit, or revert/return `false` to deny it.
     */
    function preFungibleCredit(
        address from,
        address token,
        int64 amount
    ) external returns (bool authorized);

    /**
     * Invoked just after a fungible token is credited to the entity.
     * May revert to roll back the transfer, or do side-effects (emit events, etc.).
     */
    function postFungibleCredit(
        address from,
        address token,
        int64 amount
    ) external;

    /**
     * Invoked just before an NFT is "received" (transferred) to the entity.
     * Return `true` to permit the receipt, or revert/return `false` to deny it.
     */
    function preNftReceipt(
        address from,
        address token,
        int64 serialNo
    ) external returns (bool authorized);

    /**
     * Invoked just after an NFT is received by the entity.
     * May revert to roll back the transfer, or do side-effects.
     */
    function postNftReceipt(
        address from,
        address token,
        int64 serialNo
    ) external;

    /**
     * Invoked just before HBAR is debited ("sent") from the entity.
     * Return `true` to permit the debit, or `false`/revert to deny it.
     */
    function preHbarDebit(
        address to,
        int64 amount
    ) external returns (bool authorized);

    /**
     * Invoked just after HBAR is debited from the entity.
     * May revert or do side-effects (such as logging, charging fees, etc.).
     */
    function postHbarDebit(
        address to,
        int64 amount
    ) external;

    /**
     * Invoked just before a fungible token is debited from the entity.
     * Return `true` to permit the debit, or revert/return `false` to deny it.
     */
    function preFungibleDebit(
        address to,
        address token,
        int64 amount
    ) external returns (bool authorized);

    /**
     * Invoked just after a fungible token is debited from the entity.
     */
    function postFungibleDebit(
        address to,
        address token,
        int64 amount
    ) external;

    /**
     * Invoked just before an NFT is sent (transferred) from the entity.
     * Return `true` to permit the send, or revert/return `false` to deny it.
     */
    function preNftSend(
        address to,
        address token,
        int64 serialNo
    ) external returns (bool authorized);

    /**
     * Invoked just after an NFT is sent (transferred) from the entity.
     */
    function postNftSend(
        address to,
        address token,
        int64 serialNo
    ) external;
}
```

All of these lambda types may be attached as either system or user lambda; but note not all future types will be
dual-purpose. A lambda is attached by specifying its source and properties,

```protobuf
/**
 * The content of a private lambda, which implies creation of a new contract.
 */
message PrivateLambda {
  oneof source {
    /**
     * Inside a ContractCreateTransactionBody, the pending contract.
     */
    bool self = 1;
    /**
     * The full initcode, including the constructor parameters.
     */
    bytes parameterized_initcode = 2;
  }
  /**
   * The gas to be used in creating the lambda contract.
   */
  required uint64 gas_limit = 3;
}

/**
 * The attachment of a lambda to an entity.
 */
message LambdaAttachment {
  oneof lambda {
    /**
     * For a private lambda, the source of the lambda's creation.
     */
    PrivateLambda private_lambda = 1;
    /**
     * For a public lambda, the ID of the contract to be used as a lambda.
     */
    ContractID public_contract_id = 2;
  }
  /**
   * Whether the lambda is a system lambda.
   */
  required bool is_system = 3;
  /**
   * For a system lambda, the gas that will be used to run the lambda;
   * for a user lambda, the minimum gas that must be supplied to run the
   * lambda.
   */
  required uint64 gas_limit = 4;
  /**
   * If set, the account that will pay for the gas when executing the
   * lambda. (Default for a system lambda is the account paying for the
   * transaction that attached the lambda. Default for a user lambda is
   * the account paying for the transaction that triggered the lambda.)
   */
  optional AccountID payer_id = 5;
  /**
   * The type or types of the lambda.
   */
  repeated LambdaType types = 5;
}
```

In principle, a lambda can be added to any persistent entity with an admin key, although this HIP only propose
added lambdas to accounts and contracts. Lambdas are attached by submitting a `LambdaAttach` transaction referencing
an existing account or contract with admin key.

```protobuf
/**
 * Attaches one or more lambdas to an entity.
 */
message LambdaAttachTransactionBody {
  oneof entity {
    /**
     * The account to which the lambda(s) are to be attached.
     */
    AccountID account_id = 1;
    /**
     * The contract to which the lambda(s) are to be attached.
     */
    ContractID contract_id = 2;
  }
  /**
   * The lambdas attachments to perform.
   */
  repeated LambdaAttachment lambda_attachments = 3;
}
```

For a new immutable contract, lambdas can also be attached in the create transaction.

```protobuf
message ContractCreateTransactionBody {
  ...
  /**
   * Any lambdas to be attached to the new contract.
   */
  repeated LambdaAttachment lambda_attachments = 20;
}
```

If a lambda is created successfully, its id reflects its index in the history of lambdas attached to its owner.

```protobuf
/**
 * Attaches one or more lambdas to an entity.
 */
message LambdaId {
  oneof owner {
    /**
     * The account owning the lambda.
     */
    AccountID account_id = 1;
    /**
     * The contract owning the lambda.
     */
    ContractID contract_id = 2;
  }
  /**
   * A unique identifier for the lambda relative to its owner.
   */
  uint64 index = 3;
}
```

Assuming top-level `SUCCESS`, the ids of the lambdas attached in a transaction are returned in the receipt; as
well as the ids of any system lambdas that were replaced by the new attachments.

```protobuf
message TransactionReceipt {
  ...
  /**
   * In the receipt of a LambdaAttach or ContractCreate, the ids of newly attached lambdas.
   */
  repeated LambdaId attached_lambda_ids = 15;
  /**
   * In the receipt of a LambdaAttach, the ids of system lambdas that were replaced by the
   * newly attached lambdas.
   */
  repeated LambdaId detached_system_lambda_ids = 16;
}
```

Detaching a lambda happens via a new `LambdaDetach` transaction.

```protobuf
/**
 * Detaches one or more lambdas from an entity.
 */
message LambdaDetachTransactionBody {
  oneof entity {
    /**
     * The account from which the lambda(s) are to be detached.
     */
    AccountID account_id = 1;
    /**
     * The contract from which the lambda(s) are to be detached.
     */
    ContractID contract_id = 2;
  }
  /**
   * The ids of the lambdas to be detached.
   */
  repeated LambdaId lambda_ids = 3;
}
```

### The lambda execution environment

We propose a new system contract, `IHederaEnvironmentService` at address `0x16c` with functions that expose information
about the top-level transaction. These functions will also have utility for some HAPI contract calls, but are designed
with lambdas in mind.

```solidity
// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.4.9 <0.9.0;
pragma experimental ABIEncoderV2;

interface IHederaEnvironmentService {
    /// Returns whether the account at the given address had an active signature on
    /// executing transaction.
    /// @param account The address of an account.
    /// @return verdict Whether the account has signed the transaction
    function isSigner(address account)
    external
    returns (bool verdict);

    /// Returns the memo of the executing transaction.
    /// @return memo The memo of the executing transaction
    function memo()
    external
    returns (string memory memo);
}
```

### User allowance lambda HAPI

Once a user attaches a system lambda of any type above, the network will automatically call it at the appropriate
extension point. For example, if a user attaches a `PRE_FUNGIBLE_CREDIT` system lambda to their account, the network will
call it every time it is about to credit fungible tokens to the account. The lambda can then return `true` to permit
the credit, or `false` to deny it, hence superseding the native protocol's behavior of checking the account signature,
even if `receiver_sig_required=true`.

Transactions can reference user allowances lambdas through extensions to the `AccountAmount` and `NftTransfer`
messages,

```protobuf
/**
 * A call to a lambda, where the owning entity is already known from context.
 */
message LambdaCall {
  /**
   * The index of the lambda to be called.
   */
  uint64 lambda_index = 1;
  /**
   * If set, an amount other than the lambda's default gas limit to use for the call.
   */
  google.protobuf.UInt64Value gas = 2;
  /**
   * If set, additional call data to be passed to the lambda.
   */
  bytes call_data = 3;
}

message AccountAmount {
  ...
  /**
   * The lambda call to be used for custom authorization of the balance adjustment.
   * If this is an HBAR credit, the scoped account must have a lambda of type
   * PRE_HBAR_CREDIT attached at this call's index. If this is a fungible token credit,
   * the token account must have a lambda of type PRE_FUNGIBLE_CREDIT attached
   * at this call's index. If this is an HBAR debit, the scoped account must have a
   * lambda of type PRE_HBAR_DEBIT attached at this call's index. If this is a fungible
   * token debit, the token account must have a lambda of type PRE_FUNGIBLE_DEBIT
   * attached at this call's index.
   */
  LambdaCall pre_lambda_call = 4;
  /**
   * The lambda call to be used for custom reaction to the balance adjustment.
   * If this is an HBAR credit, the scoped account must have a lambda of type
   * POST_HBAR_CREDIT attached at this call's index. If this is a fungible token
   * credit, the token account must have a lambda of type POST_FUNGIBLE_CREDIT
   * attached at this call's index. If this is an HBAR debit, the scoped account
   * must have a lambda of type POST_HBAR_DEBIT attached at this call's index. If
   * this is a fungible token debit, the token account must have a lambda of type
   * POST_FUNGIBLE_DEBIT attached at this call's index.
   */
  LambdaCall post_lambda_call = 5;
}

message NftTransfer {
  ...
  /**
   * The sender lambda call to be used for custom authorization of the transfer.
   * The sender account must have a lambda of type PRE_NFT_SEND attached at this
   * call's index.
   */
  LambdaCall sender_pre_lambda_call = 5;
  /**
   * The receiver lambda call to be used for custom authorization of the transfer.
   * The receiver account must have a lambda of type PRE_NFT_RECEIVE attached
   * at this call's index.
   */
  LambdaCall receiver_pre_lambda_call = 6;
  /**
   * The sender lambda call to be used for custom reaction to the transfer.
   * The sender account must have a lambda of type POST_NFT_SEND attached
   * at this call's index.
   */
  LambdaCall sender_post_lambda_call = 7;
  /**
   * The receiver lambda call to be used for custom authorization of the transfer.
   * The receiver account must have a lambda of type POST_NFT_SEND attached
   * at this call's index.
   */
  LambdaCall receiver_post_lambda_call = 8;
}
```

### Examples

We offer three examples for how allowance lambdas can be used to customize the native protocol.

#### HTS receiver signature required

Our first example is a contract that, when attached as a system lambda to an account with types `PRE_FUNGIBLE_CREDIT`
**and** `PRE_NFT_RECEIPT`, requires a designated account to have signed the executing transaction to permit a fungible
credit or NFT receipt to succeed; but has no effect on HBAR credits.

```solidity
// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

import "./IHederaEnvironmentService.sol";

contract ReceiverSigRequiredIfNotHbar {
    address private constant HEDERA_ENV_SERVICE = address(0x16c);
    address public immutable requiredSigner;

    constructor(address _requiredSigner) {
        _requiredSigner = _requiredSigner;
    }

    function preFungibleCredit(
        address from,
        address token,
        int64 amount
    ) external override returns (bool authorized) {
        return IHederaEnvironmentService(HEDERA_ENV_SERVICE).isSigner(requiredSigner);
    }

    function preNftReceipt(
        address from,
        address token,
        int64 serialNo
    ) external view returns (bool authorized) {
        return IHederaEnvironmentService(HEDERA_ENV_SERVICE).isSigner(requiredSigner);
    }
}
```

#### One-time use HBAR credit allowance

Our second example is a contract that, when attached as a user lambda to an account with `receiver_sig_required`, could
function as a one-time authorization for a counterparty to send HBAR to the account. The counterparty must use a
memo that is exactly the shared secret hashing to the bytes passed into the lambda; and then reference the `LambdaId`
as the `pre_lambda_call` in the `AccountAmount` of the `CryptoTransfer` transaction.

```solidity
// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

import "./IHederaEnvironmentService.sol";

contract OneTimeHbarCreditAllowance {
    address private constant HEDERA_ENV_SERVICE = address(0x16c);

    // The value the memo must hash to
    bytes32 public immutable secretHash;
    bool public used;

    constructor(bytes32 _secretHash) {
        secretHash = _secretHash;
        used = false;
    }

    function preHbarCredit(
        address from,
        int64 amount
    ) external payable returns (bool authorized) {
        if (used) return false;
        string memory memo = IHederaEnvironmentService(HEDERA_ENV_SERVICE).memo();
        bytes32 memoHash = keccak256(abi.encodePacked(memo));
        used = memoHash == secretHash;
        return used;
    }
}
```

#### Contract fallback functions

Our third example is a contract that, when specifying itself as a system lambda on creation, can run its fallback
function whenever it receives HBAR via the native `CryptoTransfer`; the example fallback simply emits an event with the
amount received. (This has been a commonly requested feature since the inception of the Hedera network!)

```solidity
// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

import "./IHederaEnvironmentService.sol";

contract HbarReceiveLogger {
    event HbarReceived(address indexed from, int64 amount);

    fallback() external payable {
        // Emit the event with msg.sender and msg.value
        emit HbarReceived(msg.sender, int64(msg.value));
    }

    function postHbarCredit(address from, int64 amount) external payable {
        emit HbarReceived(from, amount);
    }
}
```

## Backwards Compatibility

This HIP adds a net new feature to the protocol. Any entity that does not attach a lambda will see
identical behavior.

## Security Implications

Because lambda executions will be subject to the same `gas` charges and throttles as normal contract executions,
they are not materially different than the existing contract execution model. The main security concern with
allowance lambdas would be much the same as with any contract: That the lambda author could make a mistake in
the code that would allow an attacker to exploit the lambda to their advantage. This can be mitigated through the
normal mechanisms of code review, testing, and auditing.

## Reference Implementation

TODO

## Rejected Ideas

1. We considered lambda formats other than EVM bytecode, but ultimately decided that EVM bytecode was the most
   accessible and powerful format for the initial implementation.

## Open Issues

TODO

## References

- [HIP-18: Custom Hedera Token Service Fees](https://hips.hedera.com/hip/hip-18)
- [HIP-904: Frictionless Airdrops](https://hips.hedera.com/hip/hip-904)
- [HIP-991: Permissionless revenue-generating Topic Ids for Topic Operators](https://hips.hedera.com/hip/hip-991)

## Copyright/license

This document is licensed under the Apache License, Version 2.0 -- see [LICENSE](../LICENSE) or (https://www.apache.org/licenses/LICENSE-2.0)
