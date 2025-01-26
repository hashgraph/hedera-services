---

hip: <HIP number (assigned by the HIP editor)>
title: Hiero lambdas and an application to allowances
author: Michael Tinker <@tinker-michaelj>
working-group: Atul Mahamuni <atul@hashgraph.com>, Richard Bair <@rbair23>, Jasper Potts <@jasperpotts>, Steven Sheehy <@steven-sheehy>, Matthew DeLorenzo <@littletarzan>
requested-by: Hashgraph
type: Standards Track
category: Service
needs-council-approval: Yes
status: Draft
created: 2025-01-25
discussions-to: <TODO>
updated: 2025-01-26
-------------------

## Abstract

We propose Hiero **lambdas**, lightweight EVM functions that users can **install** on their entities to extend and
customize the native protocol. After a lambda is installed to an entity, relevant transactions reference it by id to
apply its custom logic.

A lambda does not have a `ContractID` or EVM address; it cannot be called via the Hedera API (HAPI); and it cannot hold
HBAR or Hedera Token Service (HTS) assets. To further encourage lightweight usage, lambdas default to an execution mode
that reverts on any opcode usage that would require the network to externalize a call trace. (Users can pay a higher fee
to enable full execution mode and externalize the call trace for a lambda.)

The **type** of a lambda determines where it can be installed, which transactions can reference it, and exactly how the
protocol applies its logic. For example, an allowance lambda can be installed on an account, referenced by a
`CryptoTransfer` transaction, and the protocol executes it to decide if the `CryptoTransfer` can succeed. All types of
lambdas use EVM **application binary interfaces (ABI)** to ensure a clear contract between the protocol and user-defined
logic.

Unlike smart contracts, which must encapsulate trust guarantees for multiple parties, Hiero lambdas belong to a single
owner who can directly update their storage via a `LambdaSStore` transaction. This streamlined design enables
fast, low-cost adjustments to a lambda with less overhead than a typical `ConsensusSubmitMessage`; and far less
than a `ContractCall`.

As an application, we also introduce a **transfer allowance** lambda type that is installable on Hiero accounts and
referenceable by `CryptoTransfer` transactions. We give two simple examples of transfer allowances. The first creates
a one-time use passcode allowance; the second refines the native `receiver_sig_required` flag to permit just HTS token
credits that do not incur custom fees.

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
lambdas, the use cases motivating HIP-18, HIP-904, and HIP-991 (and many other past and future enhancements) could be
realized at the protocol layer with less complexity and a broader feature set. By avoiding new protocol-level
changes for every customization, lambdas can greatly streamline innovation while maintaining the performance and
integrity of Hedera’s native services.

## Specification

First we sketch how lambdas interact with a Hiero network in terms of charging, throttling, and execution environment.
The detailed lambda protobuf API follows.

### Charging

A primary concern for lambdas is determining which account pays for the EVM gas. We propose two charging patterns that
should accommodate most use cases. When installing a lambda, the owner may choose one of these patterns.
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
transaction referencing the lambda.)

### Throttling

We propose that lambdas be subject to the same gas throttle as top-level contract calls. Specifically, when a lambda
executes, its initial EVM sender address is the payer of the referencing transaction. If this payer is a system account,
no throttles are applied. Otherwise, if the network is at capacity for gas usage, lambda execution can be throttled on
that basis and the referencing transaction will roll back with final status of `LAMBDA_EXECUTION_THROTTLED`.

### Lambda execution environment

Although a lambda exists in the network state with  bytecode and storage, it does **not** have a directly callable
EVM address. You cannot invoke it through the HAPI or reference it from other smart contracts by EVM address.

Instead, when a lambda is executed, both the EVM receiver and contract address in the initial frame are set to a
reserved system contract address, `0x16c`.

Lambdas execute by default in a mode that reverts on any opcode usage that would require the network to externalize a
call trace. (For example, include the `CALL`, `CREATE`, and `LOG` opcode variants.) This default mode is intended to
encourage lightweight usage. Users can pay a higher fee to enable full execution mode and externalize the call trace
for a lambda.

### Core HAPI protobufs

A lambda's type is one of an enumeration that now includes just the transfer allowance lambdas,

```protobuf
/***
 * The types of Hiero lambdas.
 */
enum LambdaType {
    /**
     * Customizes an account's authorization strategy for the CryptoTransfer transaction.
     */
    TRANSFER_ALLOWANCE = 0;
}
```

The charging patterns are as above,

```protobuf
/**
 * The charging patterns for Hiero lambdas.
 */
enum LambdaChargingPattern {
    /**
     * The payer of the transaction that references the lambda is charged
     * for all used gas. They receive the normal refund for unused gas.
     */
    CALLER_PAYS = 0;
    /**
     * The referencing transaction's payer is initially charged, but receives
     * a _full refund_ if the lambda does not revert. In that successful scenario,
     * a designated account that authorized the lambda's installation pays for the
     * gas actually consumed.
     */
    CALLER_PAYS_ON_REVERT = 1;
}
```

To install a lambda, you must specify its type and how to initialize its bytecode and storage. You can do this by
providing EVM initcode to execute with a constructor call; or, for especially lightweight lambdas, by providing
pre-initialized bytecode and storage slots.

You may optionally specify a charging pattern, default gas limit, and a specific lambda index to try to install the
lambda at. (If this index is already taken, the installing transaction will fail with final status
`LAMBDA_INDEX_IN_USE`.)

```protobuf
/**
 * Specifies the initcode for a lambda that wants to initialize its
 * bytecode via a EVM contract creation transaction.
 */
message LambdaInitcode {
  oneof source {
    /**
     * The ID of the file that contains the lambda's initcode.
     */
    FileID file_id = 1;

    /**
     * The lambda's initcode, inline.
     */
    bytes code = 2;
  }

  /**
   * The parameters to pass to the lambda's constructor.
   */
  bytes constructor_parameters = 3;

  /**
   * The gas limit to use when executing the lambda's initcode.
   */
  uint64 gas_limit = 4;
}

/**
 * A slot in the storage of a lambda.
 */
message LambdaStorageSlot {
  /**
   * The 32-byte key of the slot; leading zeros may be omitted.
   */
  bytes key = 1;
  /**
   * If the slot is present, the 32-byte value of the slot;
   * leaving this field empty in an update removes the slot.
   */
  bytes value = 2;
}

/**
 * Specifies the bytecode and initial storage slots for a lambda
 * that wants to initialize itself explicitly.
 */
message LambdaExplicitInit {
  oneof bytecode_source {
    /**
     * The ID of a file that contains the lambda's
     * pre-initialized bytecode.
     */
    FileID bytecode_file_id = 3;

    /**
     * The lambda's bytecode, inline.
     */
    bytes bytecode = 4;
  }

  /**
   * The initial storage of the lambda.
   */
  repeated LambdaStorageSlot storage_slots = 5;
}

/**
 * Defines how to install a lambda.
 */
message LambdaInstallation {
  /**
   * The type of lambda to install.
   */
  LambdaType type = 1;

  oneof init_method {
    /**
     * If the lambda should be initialized via a EVM contract
     * creation transaction, the initcode to execute.
     */
    LambdaInitcode initcode = 2;

    /**
     * If the lambda should be initialized explicitly, the
     * bytecode and initial storage slots to use.
     */
    LambdaExplicitInit explicit_init = 3;
  }

  /**
   * The charging pattern to apply for gas usage.
   */
  LambdaChargingPattern charging_pattern = 5;

  /**
   * If present, the default gas limit to use when
   * executing the lambda.
   */
  google.protobuf.UInt64Value default_gas_limit = 6;

  /**
   * If present, a specific lambda index to attempt
   * to install this lambda at.
   */
  google.protobuf.UInt64Value index = 7;
}
```

Once a lambda is installed, it receives an id combining its owner's id and a unique index,

```protobuf
/**
 * The id of an entity that owns a lambda.
 */
message LambdaOwnerID {
  oneof owner_id {
    /**
     * The account owning the lambda.
     */
    AccountID account_id = 1;
  }
}

/**
 * The complete identifier of a lambda.
 */
message LambdaID {
  /**
   * The id of the lambda's owner.
   */
  LambdaOwnerID owner_id = 1;

  /**
   * A unique identifier for the lambda relative to its owner.
   */
  uint64 index = 2;
}
```

where the `owner_id` choices will expand to other types of ids as lambdas are added to more entity types.

The indexes of newly installed lambdas will appear in the legacy `TransactionReceipt` if records streams are enabled,

```protobuf

message TransactionReceipt {
  // ...

  /**
   * In the receipt of a successful create or update transaction for an entity that supports lambdas,
   * the indexes of any newly installed lambdas.
   */
  repeated uint64 installed_lambda_indexes = 16;
}
```

Once a lambda exists, a transaction can reference it by specifying call details,

```protobuf
/**
 * Defines how to call a lambda from within a transaction where
 * the lambda owner is implied by the point of use. (For example,
 * it would never make sense to try to use a transfer allowance
 * for account 0.0.X inside an AccountAmount that debits account
 * 0.0.Y; we only need to give the index of which lambda owned
 * by 0.0.Y that we want to use.)
 */
message LambdaCall {
  /**
   * The index of the lambda to call.
   */
  uint64 index = 1;

  /**
   * If not empty, extra call data to pass to the lambda.
   */
  bytes call_data = 2;

  /**
   * If present, an explicit gas limit to use.
   */
  google.protobuf.UInt64Value gas_limit = 3;

  /**
   * If true, enables full execution mode at a higher price
   * and externalizes the lambda's call trace as a child
   * transaction.
   */
  bool requires_externalization = 4;
}
```

The lambda owner can update the lambda's storage directly via a `LambdaSStore` transaction,

```protobuf
/**
 * Adds or removes key/value pairs in the storage of a lambda. The lambda's owning key must sign the transaction.
 */
message LambdaSStoreTransactionBody {
  /**
   * The id of the lambda whose storage is being updated.
   */
  LambdaID lambda_id = 1;

  /**
   * The updates to the storage of the lambda.
   */
  repeated LambdaStorageSlot storage_slots = 2;
}
```

### Core system protobufs

Lambdas will be implemented by internal dispatch from each entity type's service to the `ContractService`.
The type of dispatch is a new `LambdaDispatchTransactionBody` with a choice of three actions.

```protobuf
/**
 * Specifies the creation of a new lambda at a specific id; the id must not exist.
 */
message LambdaCreation {
  /**
   * The id of the lambda.
   */
  LambdaID lambda_id = 1;

  /**
   * The installation details.
   */
  LambdaInstallation installation = 2;
}

/**
 * Specifies the execution of a lambda by its owner id and
 * the details of the call.
 */
message LambdaExecution {
  /**
   * The id of the lambda's owner.
   */
  LambdaOwnerID owner_id = 1;

  /**
   * The call details.
   */
  LambdaCall call = 2;
}

/**
 * Dispatches a lambda action to the ContractService.
 */
message LambdaDispatchTransactionBody {
  oneof action {
    /**
     * The id of the lambda to delete.
     */
    LambdaID lambda_id_to_delete = 1;

    /**
     * The creation of a new lambda.
     */
    LambdaCreation creation = 2;

    /**
     * A call to an existing lambda.
     */
    LambdaExecution execution = 3;
  }
}
```

If a `LambdaCreation` initializes the new lambda's state using an EVM transaction, or a `LambdaExecution` runs with
`requires_externalization=true`, the block stream will include a synthetic `ContractCreate` or `ContractCall`
to system contract `0x16c` with a full call trace of the lambda's execution. No other lambda dispatch has block stream
footprint aside from its state changes.

When a lambda is installed, its representation in `ContractService` state is as follows,

```protobuf
/**
 * The representation of a lambda in state, including the previous and next indexes of its owner's lambda list.
 */
message LambdaState {
  /**
   * The internal-only contract id of the lambda, used to reference its bytecode and storage
   * without defining duplicate lambda-scoped state keys for lambda bytecode and storage.
   * This id <b>cannot</b> be used to call the lambda via HAPI.
   */
  proto.ContractID contract_id = 1;

  /**
   * The type of the lambda.
   */
  proto.LambdaType type = 2;

  /**
   * The charging pattern of the lambda.
   */
  proto.LambdaChargingPattern charging_pattern = 3;

  /**
   * If set, the default gas limit to use when executing the lambda.
   */
  google.protobuf.UInt64Value default_gas_limit = 4;

  /**
   * True if the lambda has been uninstalled.
   */
  bool deleted = 5;

  /**
   * The lambda's first contract storage key.
   */
  bytes first_contract_storage_key = 6;

  /**
   * If non-zero, the index of the lambda preceding this one in the owner's
   * doubly-linked list of lambdas.
   */
  uint64 previous_index = 7;

  /**
   * If non-zero, the index of the lambda following this one in the owner's
   * doubly-linked list of lambdas.
   */
  uint64 next_index = 8;
}
```

### Allowance lambda HAPI protobufs

The transfer allowance lambda type is the first and only lambda type in this proposal. It is installed on an account
via either a `CryptoCreate` or `CryptoUpdate` transaction. That is, we extend the `CryptoCreateTransactionBody`
with a `lambda_installations` field, and the `CryptoUpdateTransactionBody` with fields to install and uninstall lambdas.

```protobuf
message CryptoCreateTransactionBody {
  // ...

  /**
   * The lambdas to install immediately after creating this account.
   */
  repeated LambdaInstallation lambda_installations = 19;
}

message CryptoUpdateTransactionBody {
  // ...

  /**
   * The lambdas to install on the account.
   */
  repeated LambdaInstallation lambda_installations = 19;

   /**
    * The indexes of the lambdas to uninstall from the account.
    */
   repeated uint64 lambda_indexes_to_uninstall = 20;
}
```

The `Account` message in `TokenService` state is extended to include the number of installed lambdas, as well as
the indexes of the first last lambdas in the doubly-linked list of lambdas installed by the account.

```protobuf
message Account {
  // ...
  /**
   * The number of lambdas currently installed on this account.
   */
  uint64 number_installed_lambdas = 36;

  /**
   * If positive, the index of the first lambda installed on this account.
   */
  uint64 first_lambda_index = 37;
}
```

For a successful such `CryptoCreate` or `CryptoUpdate`, the indexes of the newly installed lambdas will appear in the
legacy record `TransactionReceipt` if record streams are still enabled. For each lambda installed by executing its
initcode, the network will externalize---in order---a preceding synthetic `ContractCreate` whose sidecars include the
lambda's bytecode. It is important to understand these synthetic creations will **not** specify a `ContractID` for the
new lambda, and will always appear to assign it EVM address `0x16c`. New `ContractID`s will be created only if the
lambda is constructed via initcode, and its constructor creates contracts.

Now we need to let a `CryptoTransfer` reference such a lambda. For this we extend the `AccountAmount` and `NftTransfer`
messages used in the `CryptoTransferTransactionBody`.

```protobuf
message AccountAmount {
  // ...
  /**
   * If set, a call to a lambda of type `TRANSFER_ALLOWANCE` installed on
   * accountID that must succeed for the transaction to occur.
   */
  LambdaCall transfer_allowance_lambda_call = 4;
}

message NftTransfer {
  // ...
  /**
   * If set, a call to a lambda of type `TRANSFER_ALLOWANCE` installed on
   * senderAccountID that must succeed for the transaction to occur.
   */
  LambdaCall sender_transfer_allowance_lambda_call = 5;

  /**
   * If set, a call to a lambda of type `TRANSFER_ALLOWANCE` installed on
   * receiverAccountID that must succeed for the transaction to occur.
   */
  LambdaCall receiver_transfer_allowance_lambda_call = 6;
}
```

Note that `NftTransfer` supports both sender and receiver transfer allowance lambdas, since the transaction may
need to use the receiver lambda to satisfy a `receiver_sig_required=true` setting.

### The transfer allowance ABI

The transfer allowance lambda ABI is as follows,

```solidity
// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.4.9 <0.9.0;
pragma experimental ABIEncoderV2;

import './IHederaTokenService.sol';

/// The interface for a transfer allowance lambda.
interface IHieroTransferAllowance {
    /// Combines HBAR and HTS asset transfers.
    struct Transfers {
        /// The HBAR transfers
        IHederaTokenService.TransferList hbar;
        /// The HTS token transfers
        IHederaTokenService.TokenTransferList tokens;
    }

    /// Combines the full proposed transfers for a Hiero transaction,
    /// including both its direct transfers and the implied HIP-18
    /// custom fee transfers.
    struct ProposedTransfers {
        /// The transaction's direct transfers
        Transfers direct;
        /// The transaction's assessed custom fees
        Transfers customFee;
    }

    /// Decides if the proposed transfers are allowed, optionally in
    /// the presence of additional context encoded by the transaction
    /// payer in the extra args.
    /// @param proposedTransfers The proposed transfers
    /// @param args The extra arguments
    /// @return true If the proposed transfers are allowed, false or revert otherwise
    function allow(ProposedTransfers memory proposedTransfers, bytes memory args) external returns (bool);
}
```

### Examples

Next we provide two examples of transfer allowance lambdas.

#### One-time passcode allowances

An NFT project prides itself on having only the very cleverest holders. They distribute their collection by daily
sending a NFT from the treasury to account `0.0.X`, and publishing a puzzle. The answer to the puzzle is a one-time
use passcode that allows the solver to collect the NFT.

In particular, the project team installs on account `0.0.X` at index `1` a transfer allowance lambda initialized from
the below Solidity contract.

```solidity
import "./IHieroTransferAllowance.sol";

contract OneTimeCodeTransferAllowance is IHieroTransferAllowance {
    /// The hash of a one-time use passcode string
    bytes32 storage passcodeHash;

    /// Allow the proposed transfers if and only if the args are the
    /// ABI encoding of the current one-time use passcode in storage.
    function allowTransfer(
        IHieroTransferAllowance.ProposedTransfers memory proposedTransfers,
        bytes memory args
    ) external override payable returns (bool) {
        (string memory passcode) = abi.decode(args, (string));
        bytes32 hash = keccak256(abi.encodePacked(passcode));
        bool matches = hash == passcodeHash;
        if (matches) {
            passcodeHash = 0;
        }
        return matches;
    }
}
```

As great aficionados of the project, we see one day that `0.0.X` holds our favorite NFT of all, serial `123`; and that a
`LambdaSStore` from `0.0.X` set the storage slot with key `0x00` to the hash
`0xc7eba0ccc01e89eb5c2f8e450b820ee9bb6af63e812f7ea12681cfdc454c4687`. We rush to solve the puzzle, and deduce the
passcode is the string, `"These violent delights have violent ends"`. Now we can transfer the NFT to our account `0.0.U`
by submitting a `CryptoTransfer` with,

```text
NftTransfer {
  senderAccountID: 0.0.X
  receiverAccountID: 0.0.U
  serialNumber: 123
  transfer_allowance_lambda_call: LambdaCall {
    index: 1
    call_data: "These violent delights have violent ends"
  }
}
```

Compare this example to the pure smart contract approach, where the project would need to write a more complex smart
contract that is aware of what serial number it currently holds; and makes calls to the HTS system contract to
distribute NFTs. Instead of the team using `LambdaSStore` to update the passcode with less overhead and cost to
the network than even a `ConsensusSubmitMessage`, they would need to submit a `ContractCall`. Instead of us using a
`CryptoTransfer` to collect our prize with maximum legibility and minimum cost, we would also need to submit a
`ContractCall` to the project's smart contract with a significantly higher gas limit and a **much** larger footprint
in the block stream.

For a trivial example like this, the cost and efficiency deltas may not seem decisive (unless the project was
running a very large number of these puzzles). But the idea of releasing contracts from the burden of duplicating
native protocol logic is deceptively powerful. The cost and efficiency savings for a complex dApp could be enormous,
unlocking entire new classes of applications that would be impractical to build on Hedera today.

#### Receiver signature waiver for HTS assets without custom fees

In this example we have our own account `0.0.Y` with `receiver_sig_required=true`, and want to carve out an exception
for exactly HTS token credits to our account with no assessed custom fees. We install an allowance lambda at index `2`
whose bytecode implements the ABI as follows,

```solidity
import "./IHederaTokenService.sol";
import "./IHieroTransferAllowance.sol";

contract CreditSansCustomFeesTokenAllowance is IHieroTransferAllowance {
    address constant receiver;

    constructor(address _receiver) {
        receiver = _receiver;
    }

    /// Allows the proposed transfers only if,
    ///   (1) The only transfers are direct HTS asset transfers
    ///   (2) The receiver is not debited
    ///   (3) The receiver is credited
    function allowTransfer(
        IHieroTransferAllowance.ProposedTransfers memory proposedTransfers,
        bytes memory args
    ) external override view returns (bool) {
        if (proposedTransfers.direct.hbar.transfers.length > 0
                || proposedTransfers.customFee.hbar.transfers.length > 0
                || proposedTransfers.customFee.tokens.length > 0) {
            return false;
        }
        bool receiverCredited = false;
        for (uint256 i = 0; i < proposedTransfers.tokens.length; i++) {
            IHederaTokenService.AccountAmount[] memory transfers = proposedTransfers.tokens[i].transfers;
            for (uint256 j = 0; j < transfers.length; j++) {
                if (transfers[j].accountID == receiver) {
                    if (transfers[j].amount < 0) {
                        return false;
                    } else if (transfers[j].amount > 0) {
                        receiverCredited = true;
                    }
                }
            }
            IHederaTokenService.NftTransfer[] memory nftTransfers = proposedTransfers.tokens[i].nftTransfers;
            for (uint256 j = 0; j < nftTransfers.length; j++) {
                if (nftTransfers[j].senderAccountID == receiver) {
                    return false;
                } else if (nftTransfers[j].receiverAccountID == receiver) {
                    receiverCredited = true;
                }
            }
        }
        return receiverCredited;
    }
}
```

## Backwards Compatibility

This HIP adds a net new feature to the protocol. Any account that does not install a lambda will see
identical behavior in all circumstances.

## Security Implications

Because lambda executions are subject to the same `gas` charges and throttles as normal contract executions,
they do not introduce any new denial of service vector.

The main security concerns with transfer allowances are the same as with smart contracts. That is,
- A lambda author could code a bug allowing an attacker to exploit the lambda.
- A malicious dApp could trick a user into installing a lambda with a backdoor for the dApp author to exploit.

Lambda authors must mitigate the risk of bugs by rigorous testing and code review. Users must remain vigilant about
signing transactions from dApps of questionable integrity.

## Reference Implementation

In progress, please see [here](https://github.com/hashgraph/hedera-services/pull/17551).

## Rejected Ideas

1. We considered lambda formats other than EVM bytecode, but ultimately decided that EVM bytecode was the most
   accessible and powerful format for the initial implementation.
2. We considered **automatic** lambdas that execute even without being explicitly referenced by a transaction.
   While this feature could be useful in the future, we deemed it out of scope for this HIP.
3. We considered giving lambdas a `ContractID` and EVM address, so they could be called through HAPI and from other
   smart contracts. The benefits seemed marginal, and we preferred to underscore the nature of a lambda as a private
   extension point with a single owner.
4. We considered adding `IHieroExecutionEnv` interface to the `0x16c` system contract with APIs available only
   to executing lambdas. While interesting, there was no obvious benefit for transfer allowances and the initial
   implementation.
5. We considered using a family of transfer allowances lambda types, one for each type of asset exchange. (That is,
   `PRE_HBAR_DEBIT`, `PRE_FUNGIBLE_CREDIT`, `PRE_NFT_TRANSFER`, and so on.) Ultimately the single `TRANSFER_ALLOWANCE`
   type seemed more approachable, especially as calls can encode any extra context the lambda's `allow()` method needs
   to efficiently focus on one aspect of the proposed transfers.

## Open Issues

No known open issues.

## References

- [HIP-18: Custom Hedera Token Service Fees](https://hips.hedera.com/hip/hip-18)
- [HIP-904: Frictionless Airdrops](https://hips.hedera.com/hip/hip-904)
- [HIP-991: Permissionless revenue-generating Topic Ids for Topic Operators](https://hips.hedera.com/hip/hip-991)

## Copyright/license

This document is licensed under the Apache License, Version 2.0 -- see [LICENSE](../LICENSE) or (https://www.apache.org/licenses/LICENSE-2.0)
