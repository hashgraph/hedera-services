# Proposal Title Goes Here

---

## Summary

This proposal builds on the `TSS-Ledger-ID` proposal by extending the `TssBaseService` with the API and
implementation for requesting ledger signatures and registering consumers of ledger signatures once they are produced.

| Metadata           | Entities                                   | 
|--------------------|--------------------------------------------|
| Designers          | Edward Wertz, Richard Bair, Michael Tinker |
| Functional Impacts | Services                                   |
| Related Proposals  | TSS-Ledger-ID                              |
| HIPS               | HIP-1, HIP-2,                              |

---

## Purpose and Context

This proposal is the 4th of 6 proposals that deliver the full Threshold Signature Scheme (TSS) capability based on
[BLS](https://en.wikipedia.org/wiki/BLS_digital_signature). This TSS capability creates a network level
private/public key pair where the public key is called the `ledger id` and is known by everyone, and the ledger's
private key is not known by anyone. Nodes in the network have been given special private/public key pairs, called
`shares`, whose signatures on a message can be aggregated to produce the equivalent signature, called a `ledger 
signature`, to the ledger's private key signing the same message. The value of our chosen TSS scheme is that a
network of nodes is able to transfer the ability to generate ledger signatures to another network of nodes without
revealing the ledger private key.

TSS Proposals:

1. The `TSS-Library` proposal contains the cryptographic primitives and algorithms needed to implement TSS.
2. The `TSS-Roster` proposal introduces the data structure of a consensus `Roster` to the platform.
3. The `TSS-Ledger-ID` proposal introduces a ledger id for existing networks.
4. This proposal (`TSS-Block-Signing`) introduces the methodology for signing blocks.
5. The `TSS-Ledger-ID-Updates` proposal covers the process of resetting and transplanting ledger ids between networks.
6. The `TSS-Ledger-New-Networks` proposal covers the process of setting up a new network with a ledger id.

This proposal, `TSS-Block-Signing` covers the following:

- `TssBaseService` API
    - Add a method for requesting ledger signatures on a message.
    - Add a method for registering consumers of ledger signatures.
- `TssBaseService` Implementation
    - Each node gossips a `TssShareSignatureTransaction` for each share signature it produces.
    - A threshold number of `TssShareSignatureTransaction` are aggregated to produce the ledger signature.

### Goals

The following are goals of this proposal:

1. That existing networks are able to create ledger signatures if the active roster has key material that can
   recover the network's ledger id.  (See `TSS-Ledger-ID`)

### Non-Goals

The following is not part of the scope of this proposal:

1. The ability to create ledger signatures for new networks.
2. Integrating this capability into the broader block-stream effort.

### Dependencies, Interactions, and Implications

Dependencies on `TSS-Library`:

- Upon availability of the API:
    - The `TssBaseService` API to be extended.
    - implementation of this proposal can proceed with mocked share signatures.
- Upon availability of the implementation:
    - implementation of this proposal can be tested with a pre-rendered set of shares, ledger private key, and ledger
      id.

Dependencies on `TSS-Ledger-ID`:

- Upon availability of the API:
    - The `TssBaseService` can be extended by this proposal.
- Upon availability of the implementation:
    - This proposal's full behavior can be tested with integration tests.

Impacts to the Services Team:

- The ability to sign blocks with TSS is blocked until this proposal delivers its capability.

Implication Of Completion:

- Signing blocks and construction of block proofs with TSS signatures is unblocked.

### Requirements

The core requirements governing the TSS proposals are stated in `TSS-Ledger-ID`.

Blocks Signing Specific Requirements:

1. A block is considered signed if its root hash has a ledger signature or a following block's root hash has a
   ledger signature.
1. TSS ledger signatures are best-effort with low probability of failure.
1. The block proofs produced by different nodes do not need to be identical.
1. Nodes attempt to sign every block.
2. The number of consecutive blocks that are not signed is tracked.
    3. TODO: This needs a metric and threshold for logging errors.

### Design Decisions

No Guaranteed Ledger Signature:

- `TssShareSignatureTransaction` are handled pre-consensus in the pre-handle phase of transaction handling
    - this is to allow the creation of signatures as fast as possible.
- The `TssShareSignatureTransaction` are not stored in the state.

#### Alternatives Considered

Guaranteed Ledger Signature Generation:

- requires storing share signatures in the state.
- requires processing share signatures after consensus, introducing a delay in block signing.

This was not selected for the following reasons:

1. block proofs do not need to be identical between nodes.
2. the probability of failure to sign a block is low.
3. speed in signing a block matters since blocks are not published without signatures once they are signable.

## Changes

The changes are presented in the following order:

1. Public API
2. Core Behaviors
3. Component Architecture
4. Configuration
5. Metrics
6. Performance

### Public API

#### Services

The `TssBaseService` is extended with the following API:

```Java
/**
 * The TssBaseService will attempt to generate TSS key material for any set candidate roster, giving it a ledger id and
 * the ability to generate ledger signatures that can be verified by the ledger id.  Once the candidate roster has
 * received its full TSS key material, it can be made available for adoption by the platform.
 * </p>
 * The TssBaseService will also attempt to generate ledger signatures by aggregating share signatures produced by 
 * calling {@link #requestLedgerSignature(byte[])}.
 *
 */
public interface TssBaseService {
    
    ...

    /**
     * Requests a ledger signature on a message hash.  The ledger signature is computed asynchronously and returned 
     * to all consumers that have been registered through {@link #registerLedgerSignatureConsumer(Consumer)}.
     *
     * @param messageHash The hash of the message to be signed by the ledger.
     */
    void requestLedgerSignature(@Nonnull byte[] messageHash);

    /**
     * Registers a consumer of pairs where the first element is the message hash and the second element is the 
     * ledger signature on the message hash.
     *
     * @param consumer the consumer of ledger signatures on message hashes. 
     */
    void registerLedgerSignatureConsumer(@Nonnull final Consumer<Pair<Bytes, PairingSignature>> consumer);
}
```

#### System Transactions

The `TssShareSignatureTransaction` is a new system transaction that is only generated by the `TssBaseService` and
not a user generated transaction. It may be possible that a node is aggregating share signature transactions from
multiple ledger signature requests. In addition to the `share_signature`, the `roster_hash` and `share_index` are
needed to identify the share public key to use for validation. The `message_hash` is needed to identify the message
that was signed. A ledger signature can be produced by a threshold number of `share_signatures` for the same
message. The threshold value for aggregation is determined by the roster indicated by the `roster_hash`.

```protobuf
message TssShareSignatureTransaction {
  /**
   * The hash of the roster containing the node whose share produced the share signature.
   */
  bytes roster_hash = 1;
  /**
   * The index of the share that produced this share signature.
   */
  uint64 share_index = 2;
  /**
   * The hash of the message that was signed by the share.
   */
  bytes message_hash = 3;
  /**
   * The share signature produced by the share.
   */
  bytes share_signature = 4;
}
```

### Architecture and/or Components

Describe any new or modified components or architectural changes. This includes thread management changes, state
changes, disk I/O changes, platform wiring changes, etc. Include diagrams of architecture changes.

Remove this section if not applicable.

### Module Organization and Repositories

Describe any new or modified modules or repositories.

Remove this section if not applicable.

### Core Behaviors

Describe any new or modified behavior. What are the new or modified algorithms and protocols? Include any diagrams that
help explain the behavior.

Remove this section if not applicable.

### Public API

Describe any public API changes or additions. Include stakeholders of the API.

Examples of public API include:

* Anything defined in protobuf
* Any functional API that is available for use outside the module that provides it.
* Anything written or read from disk

Code can be included in the proposal directory, but not committed to the code base.

Remove this section if not applicable.

### Configuration

Describe any new or modified configuration.

Remove this section if not applicable.

### Metrics

Are there new metrics? Are the computation of existing metrics changing? Are there expected observable metric impacts
that change how someone should relate to the metric?

Remove this section if not applicable.

### Performance

Describe any expected performance impacts. This section is mandatory for platform wiring changes.

Remove this section if not applicable.

---

## Test Plan

### Unit Tests

Describe critical test scenarios and any higher level functionality tests that can run at the unit test level.

Examples:

* Subtle edge cases that might be overlooked.
* Use of simulators or frameworks to test complex component interaction.

Remove this section if not applicable.

### Integration Tests

Describe any integration tests needed. Integration tests include migration, reconnect, restart, etc.

Remove this section if not applicable.

### Performance Tests

Describe any performance tests needed. Performance tests include high TPS, specific work loads that stress the system,
JMH benchmarks, or longevity tests.

Remove this section if not applicable.

---

## Implementation and Delivery Plan

How should the proposal be implemented? Is there a necessary order to implementation? What are the stages or phases
needed for the delivery of capabilities? What configuration flags will be used to manage deployment of capability? 
