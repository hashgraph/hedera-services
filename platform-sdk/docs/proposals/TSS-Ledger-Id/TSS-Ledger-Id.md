# TSS-Ledger-ID

---

## Summary

This proposal is for the integration of a threshold signature scheme into the consensus nodes to create a
private/public key pair for the ledger that can be used to sign blocks and other messages. The private key must be a
secret that no one knows. The public key is known by all and functions as the ledger id. The consensus nodes must be
able to aggregate their individual signatures on a message into a valid ledger signature that is verifiable by the
ledger id. Part of our goal is that users could write EVM smart contracts that can verify the block signatures.

|      Metadata      |                  Entities                  |
|--------------------|--------------------------------------------|
| Designers          | Edward, Cody, Austin, Kore, Anthony, Artem |
| Functional Impacts | Services, DevOps                           |
| Related Proposals  | TSS-Library, TSS-Roster, TSS-Block-Signing |
| HIPS               | N/A                                        |

---

## Purpose and Context

The purpose of this proposal is to integrate a Threshold Signature Scheme (TSS)
based on [BLS](https://en.wikipedia.org/wiki/BLS_digital_signature) into
consensus nodes to produce a network ledger id and offer a mechanism for
aggregating signature messages such that the final result can be verified
using the ledger id.

In this TSS based on BLS, the network is assigned a durable private/public key
pair where the public key is the ledger id and the private key is a secret that
no entity knows. Each node in the network is given a number of shares
proportional to its consensus weight and each share is a BLS key on the same
elliptic curve as the ledger key. When the network needs to sign a message,
each node uses its shares to sign that message (e.g. a block root hash) and
gossips the signature out to the network. When a node has collected enough
signatures to meet a configured threshold on the same message, it can then
aggregate the signatures into a final ledger signature that is space-efficient
and is verifiable with the ledger id.

The TSS effort has been broken down into 5 separate proposals: TSS-Library, TSS-Roster, TSS-Ledger-ID,
TSS-Block-Signing, and TSS-Ledger-ID-Updates.

1. The `TSS-Library` proposal contains the cryptographic primitives and algorithms needed to implement TSS.
2. The `TSS-Roster` proposal introduces the data structure of a consensus `Roster` to replace the platform's concept of
   an `AddressBook` and modifies the life-cycle for when the platform receives new consensus rosters.
3. This proposal (`TSS-Ledger-ID`) depends on the first two proposals and describes
   the integration of a threshold signature scheme into the consensus node, giving
   the network a ledger id that can verify network signatures.
4. The `TSS-Block-Signing` proposal is everything needed to support the signing of blocks.
5. The `TSS-Ledger-ID-Updates` proposal covers the process of resetting and transplanting ledger ids between networks.

This proposal, `TSS-Ledger-ID`, covers changes to the following elements:

- The process of generating the ledger key pair for new networks and existing networks.
- The process of transferring the ability of the ledger to sign a message from one set of consensus nodes to another.
- The new state data structures needed to store TSS Key material and the ledger id.
- The new components needed in the framework to support creating and transferring the ledger key.
- The modified startup process to initialize the signing capability.
- For each node, the creation of a new, private to the node, Elliptic Curve (EC)
  key pair, which we call the `tssEncryptionKey`, that is used in creating and
  decrypting shares.

### Goals

The following capabilities are goals of this proposal:

1. `TSS Bootstrap on Existing Network` - Able to setup an existing network with a ledger public/private key pair.
2. `TSS Bootstrap for New Network` - Able to setup a new network with a ledger public/private key pair.
3. `Keying The Next Roster` - Able to transfer the ability to sign a message with the ledger private key from one
   set of consensus nodes to another.
4. Testing of the TSS signing capability with verification by an EVM smart contract using the ALT_BN128
   [precompiles that are available in the EVM](https://github.com/ethereum/EIPs/blob/master/EIPS/eip-1829.md).

### Non-Goals

The following are not goals of this proposal:

1. Achieving a fully dynamic address book life-cycle.
2. Public API for signing blocks.
3. Removing or replacing the use of the RSA key in
   1. Signing and verifying events.
   2. Signing and verifying the state.
   3. ISS detection.
4. Modifying how reconnect works.
5. Explaining how state transplants between networks or resets of the ledger id work.

### Dependencies, Interactions, and Implications

Dependencies on the TSS-Library

- At minimum, the top-level API in the TSS-Library is defined and can be mocked for initial development.
- The complete implementation of the TSS-Library is required for the TSS-Ledger-ID proposal to be fully implemented.
- Consensus nodes must have their `tssEncryptionKey` generated with the private key stored on disk and the public key
  stored in the roster prior to the TSS-Ledger-ID proposal being delivered.

Dependencies on the TSS-Roster

- Delivery of TSS-Ledger-ID will require that the TSS-Roster proposal is delivered in a prior release.
- Development of the TSS-Ledger-ID proposal is dependent on TSS-Roster proposal in the following ways:
  - The `Roster` data structures must be defined and stored in state.
  - To modify the roster adoption lifecycle with TSS conditions, the TSS-Roster life-cycle must be implemented.

Impacts of TSS-Ledger-ID to Services Team

- The timing of submitting the candidate roster may need to be adjusted to allow enough time for the candidate
  roster to have key material generated.
- Services will need to know or detect when the candidate roster fails to be adopted due to not having enough key
  material generated.
- More than half and preferably all the nodes' public `tssEncryptionKeys` will need to be added to the address book.
  - This requires modification of the HAPI transactions that manage the address book.

Impacts to DevOps Team

- Each consensus node will need a new `tssEncryptionKey` in addition to the existing RSA key.
  - EC Key generation will have to happen before a node can join the network.
- A new node added to an existing network will need to be given a state from an existing node after the network has
  adopted the roster containing the new node. The new node must be in the active roster to join the network.
- If a node is added, removed, or updated in the address book and the candidate roster
  is not adopted in the next software upgrade, the change to that node is not applied.
- config only software upgrades can be used to trigger the adoption of a candidate roster, if the candidate roster
  has received enough yes votes.

Implications Of Completion

- The network will have a public ledger id that can verify ledger signatures.
- The active consensus nodes can transfer the ability to sign messages to the next set of consensus nodes.
- `TSS-Block-Signing` and `TSS-Ledger-ID-Updates` proposals becomes unblocked for development.

### Requirements

TSS Core Requirements

| Requirement ID |                                                        Requirement Description                                                        |
|----------------|---------------------------------------------------------------------------------------------------------------------------------------|
| TSS-001        | The ledger MUST have a public/private signing key pair that does not change when the consensus roster changes.                        |
| TSS-002        | The ledger private key MUST be a secret that nobody knows.                                                                            |
| TSS-003        | The `ledger id` MUST be a public key that everyone knows and that is able to verify signatures from the ledger private key.           |
| TSS-004        | The ledger signature MUST be produced through aggregating signatures from consensus nodes representing at least 1/3 consensus weight. |
| TSS-005        | There MUST be a way to bootstrap an existing network's consensus roster with the ledger public/private signing key                    |
| TSS-006        | There MUST be a way for a new network to generate a public/private signing key for the ledger.                                        |
| TSS-007        | There MUST be a way to rotate the ledger's public/private signing key.                                                                |
| TSS-008        | The ledger signature SHOULD be verifiable by an EVM smart contract without excessive gas cost.                                        |
| TSS-009        | The TSS implementation SHOULD be able to switch elliptic curves.                                                                      |
| TSS-010        | The TSS algorithm SHOULD be able to model consensus weight with high precision.                                                       |
| TSS-011        | The TSS design SHOULD minimize the number of steps a Node Operator is required to perform.                                            |

Block Signing Requirements

| Requirement ID |                                   Requirement Description                                   |
|----------------|---------------------------------------------------------------------------------------------|
| TSSBS-001      | The initial block in the block stream SHOULD be signable and verifiable with the ledger id. |
| TSSBS-002      | On a new network, the initial block MUST be block 1                                         |

### Design Decisions

#### New Address Book Life-Cycle

The TSS-Ledger-ID proposal modifies the address book and roster lifecycle presented in TSS-Roster as follows:

1. The `Roster` format does not change.
2. The app must set the `candidate roster` early enough to allow the nodes to generate the key material for the new
   roster before the software upgrade.
3. If the `candidate roster` does not have enough key material after a software upgrade, it will not replace the
   `active roster` and remain the candidate roster. The platform will continue to attempt to key the candidate
   roster after software upgrade.
4. The app must detect whether or not the `candidate roster` became the `active roster`.

![TSS Roster Lifecycle](./TSS-Roster-Lifecycle.drawio.svg)

#### TSS Algorithm

The threshold signature scheme chosen for our implementation is detailed in [Non-interactive distributed key
generation and key resharing (Groth21)](https://eprint.iacr.org/2021/339). This scheme is able to meet the
core requirements listed above. The rest of this proposal details the design of how to implement this scheme in the
consensus nodes. See the TSS-Library proposal for more details on the Groth21 algorithm.

The Groth21 algorithm uses [Shamir Secret Sharing](https://en.wikipedia.org/wiki/Shamir%27s_secret_sharing) to hide the
private key of the ledger and distribute shares of it so that a threshold number of node signatures on a message can
generate the ledger signature on the same message. The threshold should be high enough to ensure at least 1 honest
share is required to participate in the signing since a threshold number of shares can also collude to recover the
ledger private key, if they so choose.

The Groth21 algorithm requires a specific class of Elliptic Curve (EC) with the
ability to produce bilinear pairings. Each node will need its own long-term EC
key pair, called a `tssEncryptionKey`, based on the chosen curve, for use in
the Groth21 TSS algorithm.
Each node receives some number of shares in proportion to the consensus
weight assigned to it. Each share is an EC key pair on the same curve.
All public keys of the shares are known, and aggregating a threshold number of
share public keys will produce the ledger id. Each node only has access to the
private key of its own shares, and none other. Each node must keep their
private keys secret because a threshold number of share private keys can
aggregate to recover the ledger private key. A threshold number of signatures
on the same message from the share private keys will aggregate to produce a
ledger signature on the message.

Transferring the ability to generate ledger signatures from one set of consensus nodes to another is done by having
each node generate a `TssMessage` for each share that fractures the share into a number of subshares or `shares of
shares`. Each `share of a share` is encrypted with the public `tssEncryptionKey` of the node in the next consensus
roster that it belongs to so that only the intended node can use the `share of a share` to recover that node's
share's private key. The collection of `TssMessages` generated from nodes in the previous consensus roster forms the
`key material` for the new consensus roster.

To bootstrap a network and generate the ledger private and public keys, each node creates a random share for
themselves and generates a `TssMessage` containing `shares of shares` for the total number of shares needed in the
consensus roster. The use of random shares at the outset creates a random ledger private key that nobody knows.

In the context of the consensus nodes using the TSS library, the term `share index` refers to the index of the share as
it is returned in the list of public shares from the tss library. The first share in the list has share index `0`. If
there are 10 shares total, the last share index is `9`. The shares are assigned to nodes in the order of NodeIds. If
Node 0 has 3 shares, then it is given share with index `0`, `1`, and `2`. If Node 1 has 2 shares, then it is given
share indexes `3` and `4`, and so on. The `TssMessages` are similarly ordered. TssMessage `0` is the message generated
for share index `0`.

![TSS re-keying next consensus roster](./Groth21-re-keying-shares-of-shares.drawio.svg)

This process of keying the candidate roster during an address book change is asynchronous and takes an
indeterminate amount of time through multiple rounds of consensus to complete. This requires saving incremental
progress in the state to ensure that the process can resume from any point if a node or the network restarts.
Switching to the candidate roster must not happen until enough nodes have voted that they have verified a threshold
number of TssMessages from the active roster. Verification succeeds when a node is able to recover the ledger id.
The first threshold number of valid TssMessages to come to consensus are used to recover the ledger id. A vote
consists of a bit vector where each bit index corresponds to the order of TssMessages as they have come through
consensus. A bit at index `i` in the vote is set to 1 if the `ith` TssMessage to come through consensus is valid
and used in the recovery of the ledger id, otherwise the bit is `0`.

##### Elliptic Curve Decisions

Our first implementation of Groth21 will use the ALT_BN_128 ([BN254](https://hackmd.io/@jpw/bn254)) elliptic curve which
has precompiles in the EVM. If the Ethereum ecosystem creates precompiles for BLS12_381, we may switch over to that
more secure curve.

##### New Elliptic Curve Node Keys

Each node will need a new `tssEncryptionKey` pair in addition to the existing RSA key pair. The `tssEncryptionKey` pair
will be used in the Groth21 algorithm to generate and decrypt `TSS-Messages`. These new EC Keys will not be used for
signing messages, only for generating the shares. It is the share keys that are used to sign messages. The public
keys of the `tssEncryptionKeys` must be in the address book.

##### Groth21 Drawbacks

The Groth21 algorithm is not able to model arbitrary precision proportions of weight assigned to nodes in the
network. For example if each node in a 4 node network received 1 share, then every share has 1/4 of the total.
If there are a total of N shares, then the distribution of weight can only be modeled in increments of (1/N) * total
weight. The cost of re-keying the network in an address book change is quadratic in the number of shares. (See the
picture of TssMessages above.) This forces us to pick a number of total shares with a max value in the thousands.
This modeling of weight uses small integer precision.

##### Calculating Number of Shares from Weight and Recovery Threshold

Given that we have a low resolution for modeling weight, the number of shares assigned to each node will be
calculated as follows using java integer arithmetic where division rounds down:

- Let `N` be the max number of shares that any node can have. (A configured value.)
- Let `maxWeight` be the max weight assigned to any node.
- Let `weight[i]` be the weight assigned to node `i`.
- Let `numShares[i]` be the number of shares assigned to node `i`.
- then `numShares[i] = (N * weight[i] + maxWeight - 1) / maxWeight`

###### Threshold for recovery of Signatures and Ledger Id.

The network must be able to tolerate less than 1/3 of the weight assigned to malicious nodes. Modeling the weight as
a fewer number of shares introduces a potential error in representation. To account for this error, we need a
guaranteed safe threshold for the number of shares that must be used to create a valid signature.

A threshold of at least `(TS + 2) / 2`, with java integer division rounding down, has been proven secure under the
following conditions:

* Let `minWeight` be the minimum non-zero weight assigned to any node.
* Let `maxWeight` be the maximum weight assigned to any node.
* Let `R = maxWeight / minWeight` be the real number ratio.
* Let `N>0` be the max number of shares that any node can have.
* If `R <= 2*N` then the threshold of `(TS + 2) / 2` is secure.

Required Theorems:

- SECURITY: If the nodes in a set together have at least 1/2 of the shares, then they have at least 1/3 of the weight.
- LIVENESS: If the nodes in a set together have more than 2/3 of the weight, then they have more than 1/2 of the shares.

Proof Sketch by Dr. Leemon Baird:

- Assume WLOG that the weights are real numbers. Further assume WLOG that the largest weight held by any node is exactly
  N, and that all nodes have positive weight (none have zero).
- For any given node, you can calculate its weight per share (wps). That's its weight divided by how many shares it
  gets. It gets the shares from the ceiling of the ratio. It's easy to show that if we assume R=2N, then this guarantees
  a wps in the range [1/2, 1] inclusive, for every share. Imagine each of its shares as a card with that wps number
  written on it. In other words, that card is a single share, and it by itself constitutes that much weight. We are
  basically dividing up a given node's weight equally among all its shares.
- Then, ignore how the shares are distributed among the nodes. Look solely at them as an arbitrary set of shares with
  a wps value assigned to each one, without worrying about how many shares get each value, or how many shares each node
  gets. We only know they are all in the range [1/2, 1].
- If you want to collect 1/3 weight and have as large a fraction of the shares as possible, then that happens when
  all your shares have wps of 1/2, and all the ones you don't collect have a wps of 1. Which means you'll have 1/2 the
  shares.
- Conversely, if you want to collect more than 2/3 the weight while holding as few shares as possible, that happens when
  you collect only shares with wps of 1, and all the rest are 1/2. And again, that means you have more than 1/2 the
  shares.
- So both theorems are proved.

###### Impacts on Service's Calculation of Weight from Staked HBars

The services layer will track staking to nodes, clipping any stake above the max, and reducing any stake below `1/R` of
the maximum clipped stake to zero (where `R` is a setting of services, set to twice the max number of shares allowed per
node).

The resulting stake (after clipping or zeroing) is passed to the platform layer, which treats it as a “weight” for
consensus, and which then generates a number of shares for each node. That share count is proportional to that weight,
and acts as a low-resolution version of that weight.

##### TSS Algorithm - Alternatives Considered

The list of options considered here were based off of prototypes developed by [Rohit Sinha](https://github.com/rsinha),
the Hashgraph cryptography expert. The Groth21 algorithm was chosen because it is efficient for use in smart
contract verification, and we can assign a multiplicity of shares to nodes to get close enough in modeling the
distribution of weight between nodes.

| Requirement | hinTS | Groth21 |
|-------------|-------|---------|
| TSS-001     | Yes   | Yes     |
| TSS-002     | Yes   | Yes     |
| TSS-003     | Yes   | Yes     |
| TSS-004     | Yes   | Yes     |
| TSS-005     | Yes   | Yes     |
| TSS-006     | Yes   | Yes     |
| TSS-007     | Yes   | Yes     |
| TSS-008     | No    | Yes     |
| TSS-009     | Yes   | Yes     |
| TSS-010     | Yes   | No      |

###### hinTS

The hinTS algorithm is a threshold signature scheme that is able to measure double precision distributions of weight
across nodes. The complicating factor with hinTS is that during an address book change over, a recursive proof needs
to be constructed to prove that the new roster is a descendent of the original genesis roster. Validation of this
recursive proof proved too expensive for EVM smart contracts, even with elliptic curve precompiles.

#### TSS Bootstrap Process

The TSS bootstrap process is the process of generating the ledger public/private key pair for a new network or an
existing network. There are several options to consider in the TSS bootstrap process:

1. Do we allow block number and round number to be out of sync?
2. Do we need the initial block produced to be signed by the ledger id?
3. For new networks, do we need to produce a signed block 1?
4. For existing networks, does the TSS bootstrap roster sign blocks prior to its existence?

##### For New Networks

There will be a pre-genesis phase where the genesis roster is keyed and a random ledger id is created. This will
happen through running the platform without application transactions. After the platform keys the roster, the
platform restarts itself with a copy of the genesis state after adding the TSS key material and ledger id
id. After the platform restarts, application transactions are accepted and the platform starts building a new
hashgraph, working towards consensus on round 1 with the ability to sign block 1. This ensures that the first user
transactions handled by the network are in a block that can have a block proof as soon as possible. The
alternative is that blocks will go without block proofs until the nodes go through TSS bootstrap, which may be
several rounds later.

While this solution does not capture the TSS bootstrap communication in the history of the hashgraph after the
platform restarts, nodes can cryptographically verify that the setup process took place correctly through using the
TSS key material to re-derive the ledger id. Each node can validate the TssMessages have come from a sufficient
variety of nodes. With the assumption that less than 1/3 of the weight is assigned to malicious nodes, the threshold
of 1/2 ensures that the malicious nodes will not be able to recover the ledger private key if they collude together.

The process of the platform restarting itself after performing a pre-genesis keying phase is new startup logic.
This should not involve an interruption of the containing process. While we are in the pre-genesis phase the
platform will not accept application transactions. Once the platform has restarted itself with a keyed roster, it
will accept application transactions when it becomes active.

A node in the pre-genesis phase cannot gossip with nodes that have restarted and are out of pre-genesis. If a
pre-genesis node connects with a post-genesis node, the post-genesis node should provide the key material to the
pre-genesis node. After validation of the key material, the pre-genesis node can restart in post-genesis mode and
connect with peers as normal. Part of the handshaking process when nodes first connect to each other will need to
include exchange of information about which mode they are in.

##### For Existing Networks

Given the requirement of TSSBS-001, the setup of the TSS Key Material must happen before the software upgrade that
enables block streams. In order to reduce risk and density of complex software changes, giving the nodes
their `tssEncryptionKeys` and changing the roster life-cycle through the TSS-Roster effort should be completed and
delivered in a software release prior to the delivery.

The schedule of delivery of capability is explored in a later section in this proposal.

The setup of the bootstrap key material will be observable in the record stream prior to the Block Streams going live,
but this observability is not consequential. What matters is the cryptographic verification that the TSS
material can generate the ledger key.

##### TSS Bootstrap Process - Alternatives Considered

| N |                                                    Option                                                     |          1-1 round to block          |                  round # == block #                  |      Ledger signs initial block      |         Ledger signs block 1         |   TSS keying in hashgraph history    |     TSS keying in block history      |        Minimal DevOps Impact         |         Implementation Complexity         |                                       Notes                                        |
|---|---------------------------------------------------------------------------------------------------------------|--------------------------------------|------------------------------------------------------|--------------------------------------|--------------------------------------|--------------------------------------|--------------------------------------|--------------------------------------|-------------------------------------------|------------------------------------------------------------------------------------|
| 1 | TSS Setup in block history, initial block > 1 and not signed                                                  | <span style="color:green">YES</span> | <span style="color:green">YES</span>                 | <span style="color:red">NO</span>    | <span style="color:red">NO</span>    | <span style="color:green">YES</span> | <span style="color:green">YES</span> | <span style="color:green">YES</span> | <span style="color:green">LOW</span>      | Mainnet if Block Streams ships before Block Proofs                                 |
| 2 | TSS Setup not in block history, initial block > 1 and is signed                                               | <span style="color:green">YES</span> | <span style="color:green">YES</span>                 | <span style="color:green">YES</span> | <span style="color:red">NO</span>    | <span style="color:green">YES</span> | <span style="color:red">NO</span>    | <span style="color:green">YES</span> | <span style="color:green">LOW</span>      | Mainnet if Block Streams ships with Block Proofs                                   |
| 3 | TSS Setup not in block history, initial block == 1 and is signed                                              | <span style="color:green">YES</span> | <br/><span style="color:red">NO</span>, fixed offset | <span style="color:green">YES</span> | <span style="color:red">NO</span>    | <span style="color:green">YES</span> | <span style="color:red">NO</span>    | <span style="color:green">YES</span> | <span style="color:orange">LOW-MED</span> | Cognitive burden to reason about block # and round # discrepancy.                  |
| 4 | TSS Setup in block history, initial block > 1, covers all prior rounds, and is signed                         | <span style="color:red">NO</span>    | <span style="color:green">YES</span>                 | <span style="color:green">YES</span> | <span style="color:red">NO</span>    | <span style="color:green">YES</span> | <span style="color:green">YES</span> | <span style="color:green">YES</span> | <span style="color:orange">MED</span>     |                                                                                    |
| 5 | TSS Setup in block history, initial block == 1, covers all prior rounds, and is signed                        | <span style="color:red">NO</span>    | <br/><span style="color:red">NO</span>, fixed offset | <span style="color:green">YES</span> | <span style="color:red">NO</span>    | <span style="color:green">YES</span> | <span style="color:green">YES</span> | <span style="color:green">YES</span> | <span style="color:orange">MED</span>     | Cognitive burden to reason about block # and round # discrepancy.                  |
| 6 | Retrospectively sign the initial blocks with TSS once it becomes available.                                   | <span style="color:green">YES</span> | <span style="color:green">YES</span>                 | <span style="color:green">YES</span> | <span style="color:green">YES</span> | <span style="color:green">YES</span> | <span style="color:green">YES</span> | <span style="color:green">YES</span> | <span style="color:red">HIGH</span>       | TSS Genesis Roster signs older blocks.  This is compatible with all other options. |
| 7 | Pre-Genesis: Separate app with genesis roster creates key material in state before network officially starts. | <span style="color:green">YES</span> | <span style="color:green">YES</span>                 | <span style="color:green">YES</span> | <span style="color:green">YES</span> | <span style="color:red">NO</span>    | <span style="color:red">NO</span>    | <span style="color:red">NO</span>    | <span style="color:red">HIGH</span>       | Applies to new networks only.  Use Option 1 or 2 for existing networks.            |
| 8 | Instead of separate app, detect genesis, key the state, restart the network with a keyed genesis state.       | <span style="color:green">YES</span> | <span style="color:green">YES</span>                 | <span style="color:green">YES</span> | <span style="color:green">YES</span> | <span style="color:red">NO</span>    | <span style="color:red">NO</span>    | <span style="color:green">YES</span> | <span style="color:red">HIGH</span>       | Applies to new networks only.  Use Option 1 or 2 for existing networks.            |

Option 2 was chosen for existing networks and Option 8 was selected for new networks. This decision was made to
ensure that the first block in the block stream can be signed by the ledger.

---

## Changes

The changes are presented in the following order:

1. Public API
2. Core Behaviors
3. Component Architecture
4. Configuration
5. Metrics
6. Performance

### Public API

The public API changes consist of the following:

- System Transactions
- State Data Structures

#### System Transactions

The following are new system transactions needed to key rosters and generate ledger signatures:

1. `TssMessageTransaction` - Used to send a TssMessage to the network for a candidate roster.
2. `TssVoteTransaction` - Used to vote on the validity of TssMessages for a candidate roster.

##### TssMessage System Transaction

```protobuf
/**
 * The TssMessageTransaction is used to send a TssMessage to the network for a candidate roster.
 */
message TssMessageTransaction {

  /**
   * An identifier for the node that generated the TssMessage.
   */
  uint64 node_id = 1;

  /**
   * A hash of the roster containing the node generating the TssMessage.
   */
  bytes source_roster_hash = 2;

  /**
   * A hash of the roster that the TssMessage is for.
   */
  bytes target_roster_hash = 3;

  /**
   * An index to order shares.<br/>
   * This establishes a global ordering of shares across all shares in
   * the network.  It corresponds to the index of the public share in the
   * list returned from the TSS library when the share was created for
   * the source roster.
   */
  uint64 share_index = 4;

  /**
   * A byte array containing the the TssMessage generated by the node
   * for the share_index.
   */
  bytes tss_message = 5;

  /**
   * A signature produced by the node.<br/>
   * This is created by signing the tuple (source_roster_hash, target_roster_hash,
   * share_index, tss_message) using the node's private `tssEncryptionKey`.
   * The signature is verified by other nodes using the public key of the
   * `tssEncryptionKey` for the node sending this transaction.
   */
  bytes signature = 6;
}
```

##### TssVote System Transaction

```protobuf
/**
 * A transaction used to vote on the validity of TssMessages for a candidate roster.
 */
message TssVoteTransaction {

  /**
   * An identifier for the node that generated this TssVote.
   */
  uint64 node_id = 1;

  /**
   * A hash of the roster containing the node generating this TssVote.
   */
  bytes source_roster_hash = 2;

  /**
   * A hash of the roster that this TssVote is for.
   */
  bytes target_roster_hash = 3;

  /**
   * An identifier (and public key) computed from the TssMessages for the target roster.
   */
  bytes ledger_id = 4;

  /**
   * A signature produced by the node.<br/>
   * This is produced using the node RSA signing key to sign the ledger_id.
   * This signature is used to establish a chain of trust in the ledger id.
   */
  bytes node_signature = 5;

  /**
    /**
     * A bit vector where the index position of each bit corresponds to the
     * sequence number of a `TssMessageTransaction`. The least significant bit
     * of byte[0] corresponds to sequence number 0 and the most significant bit
     * of byte[0] corresponds to sequence number 7.  The least significant bit
     * of byte[1] corresponds to sequence number 8 and the most significant bit
     * of byte[1] corresponds to the sequence number 15, and so on. If a bit is
     * set to 1, then the TssMessage for the corresponding TssMessageTransaction
     * sequence number was received as one of the first Threshold number of
     * valid TssMessages in consensus order.  If a bit is set to 0, then the
     * TssMessage was invalid or there was no TssMessageTransaction with a
     * sequence number corresponding to the bit index.
     */
  */
  bytes tss_vote = 5;

  /**
   * A signature produced by the node signing the tuple
   * (source_roster_hash, target_roster_hash, ledger_id, node_signature, tss_vote)
   * using its private `tssEncryptionKey`.
   * The signature is verified by other nodes using the public key of the
   * `tssEncryptionKey` for the node sending this transaction.
   */
  bytes signature = 6;
}
```

#### State Datastructures

The state needs to store the relevant ledger id and TSS Data (key material and votes).

##### Ledger Id Queue

The `ledgerId` is the public ledger key able to verify ledger signatures. It is used by the platform and other
entities outside the consensus node. This value should not change during normal address book changes and its value does
not change unless the network goes through another TSS bootstrap process. When the value changes, to transfer trust,
the old ledger private key should sign the new ledger id or the nodes should sign the new ledger id with their RSA
signing keys, or both. Quads of (ledger id, round, ledger signature, map<nodeid, node_signature>) are stored in a
list in a singleton to record the history of ledger ids on the network.

NOTE: The detailed processes for updating the ledger id can be found in the `TSS-Ledger-ID-Updates` proposal.

```protobuf
/**
 * A public key that identifies the ledger and can be used to verify ledger signatures.
 */
message LedgerId {

  /**
   * The new ledger id and public key that can verify ledger signatures.  Must not be null.
   */
  bytes ledger_id = 1;

  /**
   * The round number when the new ledger id becomes active.  Must not be null.
   */
  uint64 round = 2;

  /**
   * The signature of the previous ledger private key on the new ledger id.  May be null.
   */
  bytes ledger_signature = 3;

  /**
   * A map from node ids to the node signatures on the ledger id.  The node signatures are produced by signing the
   * ledger id with the node's RSA private key.
   */
  map<uint64, bytes> node_signatures = 4;
}
```

##### TSS Data Maps

There are two maps of tss related data stored in the state through the public state API.

1. `TssMessageMap` - A map of TssMessageTransactions that have come to consensus for a roster.
2. `TssVoteMap` - A map of TssVoteTransactions that have come to consensus for a roster.

Keys in the `TssMessageMap` have the following structure:

```protobuf
/**
  * A key for use in the TssMessageMaps.
  */
message TssMessageMapKey {

  /**
   * The roster hash is the hash of the target roster that the value in the tss data map is related to.
   */
  bytes roster_hash = 1;

  /**
   * The sequence number is the order in which the mapped value came to consensus for the given roster hash.
   */
  uint64 sequence_number = 2;
}
```

The values in the `TssMessageMap` are of type `TssMessageTransaction` where the `target_roster_hash` in the value is
the `roster_hash` in the key.

Keys in the `TssVoteMap` have the following structure:

```protobuf
/**
  * A key for use in the TssVoteMaps.
  */
message TssDataMapKey {

  /**
   * The roster hash is the hash of the target roster that the value in the tss data map is related to.
   */
  bytes roster_hash = 1;

  /**
   * The node id of the node that generated the TssVote.
   */
  uint64 node_id = 2;
}
```

The values in the `TssVoteMap` are of type `TssVoteTransaction` where the `target_roster_hash` in the value is the
`roster_hash` in the key. The order in which votes come to consensus is not important. Greater than 1/3 of the weight
must contribute yes votes that have the same vote structure for the candidate roster to be adopted. This ensures
that at least 1 honest node has voted yes.

Lifecycle Invariants

1. Inserts should only be made for candidate rosters that are going through the keying process.
2. When a roster is no longer stored in the state, the corresponding key material and votes should be removed.
3. Every roster that has become active after the TSS bootstrap process must have sufficient key material to be
   able to recover the ledger id.

### Core Behaviors

The new behavior is related to the bootstrap process of creating the ledger id and transferring the ability to sign
messages from one roster to the next roster

#### Startup for New Networks

At the start of a new network there will be a new pre-genesis phase to generate the key material for the genesis
roster and then restart the network adding the new key material and ledger id to the genesis state.

The following startup sequence on new networks is modified from existing practices.

1. The app reads the genesis address book and cryptography from disk.
2. The app hands an optional state, genesis roster, and private keys to the platform.
3. The platform validates that its private `tssEncryptionKey` matches its public `tssEncryptionKey` in the genesis
   roster. If there is a mismatch, a critical error is logged and the node shuts down.
4. The platform copies the genesis state and starts gossiping with peers in a pre-genesis mode with the following
   behavior:
   1. User transactions are not accepted from the app and events containing user transactions are ignored.
   2. A node in pre-genesis mode can only gossip with other peers in pre-genesis mode.
   3. If a pre-genesis node encounters a peer that is post-genesis, the post-genesis node sents the address book
      and key material and the pre-genesis node restarts itself after successfully validating the key material.
   4. If the key material cannot be validated, the error is logged and the node remains in pre-genesis mode
      connecting to other peers.
   5. If all peers are in post-genesis and no peer can provide validatable key material, the node logs a critical
      error and shuts itself down.
   6. Active nodes in pre-genesis mode will initiate the tss-bootstrap process by generating a random share for
      themselves and create a TssMessage with shares of shares for the total number of shares needed in the
      genesis roster.
   7. When a pre-genesis node has enough key-material to generate the ledger id, the node votes yes that it is ready to
      adopt the key material.
   8. When a node detects that a threshold number of pre-genesis nodes on the network are ready to adopt the key
      material, the node adds the key material and ledger id to a copy of the genesis state and restarts the platform.
5. Once a node has restarted with the key material and ledger id, the node will start accepting user transactions and
   building a new hashgraph, working towards consensus on round 1. It is now in post-genesis mode.
6. If any pre-genesis nodes connects to the post-genesis node, the post-genesis node provides the pre-genesis node a
   copy of the tss key-material and ledger id. This is the only communication supported with pre-genesis nodes. A
   post genesis node can count or rate limit the number of times it has provided the key material to a pre-genesis
   node to prevent an intentional or unintentional denial of service attack.

#### Startup for Existing Networks

##### The Release Containing the `tssEncryptionKey`

This release must occur with or after the complete deployment of the TSS-Roster proposal.

This release contains all the state data structures needed to store the public `tssEncryptionKey` in the state and all
code needed to support storing and retrieving the private `tssEncryptionKey` on disk and managing the `tssEncryptionKey`
life cycle, including key rotation.

The release supporting the addition of the `tssEncryptionKey` to the roster also provides modified HAPI
transactions for updating the address book with a node's `tssEncryptionKey`. Until the next software upgrade, only
the candidate roster will have the `tssEncryptionKey` possibly set in roster entries.

The only change to the startup sequence is that the `tssEncryptionKey` private key is read from disk, provided to
the platform through the `PlatformBuilder` and validated against the public `tssEncryptionKey` in the active roster.
A mismatch is a critical error that will cause the node to shut down.

##### The Release Containing the TSS Bootstrap Process

This release must be at least 1 release after the `tssEncryptionKey` release.

The following startup sequence occurs: (This sequence should work for this release and all subsequent releases
unless the startup sequence is modified by subsequent releases.)

1. The app reads the state and cryptography from disk (including support for reading the private `tssEncryptionKey`)
2. The app hands the state and private keys to the platform.
3. The platform validates that its private `tssEncryptionKey`  matches its public `tssEncryptionKey` in the active
   roster. If there is a mismatch, a critical error is logged and the node shuts down.
4. If a software upgrade is detected,
   1. If the existing active roster and candidate roster do not have complete key material and the network does not
      yet have a ledger id, adopt the candidate roster immediately on software upgrade.  (This keeps the pre-ledger
      id behavior of the network until the ledger id can be created for the first time.)
   2. If the candidate roster exists, has key material, and the number of yes votes passes the threshold for
      adoption, then the candidate roster is adopted.
      1. Validation Check: if the state already has a ledger id set, the ledger id recovered from the key material
         must match the ledger id in the state.
      2. If there is no ledger id in the state and the previously active roster has no key material, the ledger
         id recovered from the candidate roster key material is set in the state as the new ledger id.
   3. In all other cases the existing active roster remains the active roster.
5. The platform goes through its normal startup sequence.
6. When the platform is (`ACTIVE`) and a candidate roster is set, the platform will start or continue the TSS bootstrap
   process to key the candidate roster.

Note: If this is release N, then if all goes well and the candidate roster is successfully keyed, the next software
upgrade (release N+1) is when the network will receive its ledger id. No additional code deployment is required to
make this happen.

##### The Cleanup Release

This release will contain any code cleanup required from the migration to the new TSS bootstrap process.

### Component Architecture

The following are new or modified components or entities in the system:

1. TSS State Manager (New, Not Wired)
2. TSS Message Creator (New, Wired)
3. TSS Message Validator (New, Wired)
4. TSS Key Manager (New, Wired)
5. TransactionResubmitter (Existing)

![TSS Platform Wiring](./TSS-Wiring-Diagram.drawio.svg)

#### TSS State Manager

The TSS state manager is executed on the transaction handling thread and is capable of reading and writing the state
during round handling. The logic will be encapsulated in a class with a well-defined API, but this will not be a
full-fledged wiring component outside the consensus round handler.

Responsibilities:

1. At node startup
   - read all rosters, `TssMessageTransactions`, and `TssVoteTransactions` from the state.
   - If a candidate roster exists and has sufficient votes to be adopted, rotate the rosters.
     - If the Ledger ID in the votes is new, add a new ledger id entry into the history in the state.
   - send the active roster to the `TssMessageCreator` and `TssMessageValidator`.
   - If it exists, send the candidate roster to the `TssMessageCreator` and `TssMessageValidator`.
   - send all `TssMessageTransactions` to the `TssMessageValidator`.
   - send all `TssVoteTransactions` to the `TssMessageValidator`.
   - wait for the pubic shares and ledger id to come back for the active roster.
   - Verify that the returned ledger id matches what exists in the state.
     - (NOTE: handling changes to the LedgerId are addressed in the `TSS-Ledger-ID-Updates` proposal.)
   - If there was no Ledger Id in the state, set the ledger Id in the state.
2. Handle `TssMessageTransaction` system transactions
   - handled in consensus order
   - Verify the signature on the `TssMessageTransaction` is valid.
   - determine the next sequence number to use for the `TssMessageMap` key.
   - insert into the `TssMessageMap` if it is legal to do so
     - don’t insert multiple messages for the same share index
   - if successfully inserted into the `TssMessageMap`, send the `TssMessageTransaction` to the `TssMessageValidator`.
3. Handle `TssVoteTransaction` system transactions
   - If a threshold number of votes (totaling at least 1/3 of weight), all with the same vote byte array, have
     already been received for the candidate roster, discard the `TssVoteTransaction`.
   - Otherwise, Verify the signature on the `TssVoteTransaction` is valid.
   - insert into the `TssVoteMap` if it is legal to do so.
     - don’t insert multiple votes from the same node, discard it if it is redundant.
   - Send the `TssVoteTransaction` to the TSS Message Validator.
4. At end of round handling
   - Detect if there is a new candidate roster set in the state.
   - send the candidate roster to the `TssMessageCreator` and `TssMessageValidator`.
   - if the previous candidate roster has been replaced, use its saved roster hash to clear the roster map of the
     old candidate roster and clear the `TssMessageMap` and `TssVoteMap` of entries related to the roster hash.

#### TSS Message Creator

The TSS message creator is responsible for generating a node’s TSSMessages for its shares. This runs as its own
asynchronous thread since this may be a computationally expensive operation.

Internal Elements:

1. active roster hash
2. candidate roster hash
3. Map<RosterHash, Roster> rosters
4. Map<RosterHash, List<PairingPrivateKey>> privateShares
5. NodeId (constructor)
6. TssEncryptionKey (constructor)

Inputs:

1. Active Roster (Wire)
2. Candidate Roster (Wire)
3. private shares (Wire)

`On Active Roster`:

1. If the new active roster is the same as the existing one, do nothing.
2. If the new active roster == the candidate roster,
   1. clear the candidate roster hash.
   2. clear the maps of the previous active roster data.
3. If there existed a previous active roster and the candidate roster is different from the new active roster, log an
   error.
4. Set the new active roster
5. If the active roster has private shares set and there exists a candidate roster, then `createTssMessageTransactions`.

`On Candidate Roster`:

1. If the internal candidate roster is set and equal to the input candidate roster, do nothing.
2. else, set the internal candidate roster.
3. If the active roster has private shares set and there exists a candidate roster, then `createTssMessageTransactions`.

`On Private Shares`:

1. record the private shares in the internal private shares map for the appropriate roster hash.
2. If the active roster has private shares set and there exists a candidate roster, then `createTssMessageTransactions`.

`createTssMessageTransactions`:

1. For each private share belonging to the active roster:
   1. Use the TSS-Library to generate a TSS Message for the share private key and public keys for the candidate roster.
   2. Submit a system `TssMessageTransaction` with the TSS Message.

#### TSS Message Validator

The TSS message validator is responsible for validating TSS messages and for submitting votes as to the validity of the
candidate roster. The TSS message validator gets its input from the TSS state manager in the form of a candidate roster
that we want to adopt, and as a sequence of TSS messages that have reached consensus.

Internal Elements:

1. active roster hash
2. candidate roster hash
3. Map<RosterHash, Roster> rosters
4. Map<RosterHash, List<TssMessageTransaction>> tssMessages
5. Map<RosterHash, List<TssVoteTransaction>> tssVotes
6. Set<RosterHash> votingClosed
7. private RSA signing key (constructor argument)

Inputs:

1. Active Roster (Wire)
2. Candidate Roster (Wire)
3. TssMessageTransaction (Wire)
4. TssVoteTransaction (Wire)
5. Public Shares and Ledger ID (Wire)

Input Invariants

1. TSS Messages for the candidate roster must be received after the candidate roster is set.
2. TSS Votes for the candidate roster must be received after the candidate roster is set.
3. A TSS Message must exist if a TSS Vote has a 1 in the vote vector for the corresponding share index.

`On Active Roster`:

1. If the new active roster is the same as the existing one, do nothing.
2. If the new active roster == the candidate roster.
   1. Clear the candidate roster hash.
   2. Clear the map data for the previous active roster.
   3. Remove the previous active roster hash from the voting closed set.
3. If a previous active roster existed and the candidate roster is different from the new active roster, log an error.
4. Set the new active roster.

`On Candidate Roster`:

1. If the input candidate roster is equal to the active roster, do nothing.
2. If the input candidate roster is equal to the internal candidate roster, do nothing.
3. Set the internal candidate roster.
4. If a previous candidate roster existed.
   1. Clear the associated `TssMessageTransaction` and `TssVoteTransaction` data.
   2. Remove the previous candidate roster hash from the voting closed set.

`On TssMessageTransaction`:

1. If the voting is closed, do nothing.
2. If a `TssVoteTransaction` exists for this node, do nothing.
3. If a TSS Message already exists for the share index and target roster, do nothing.
4. Validate the TssMessage and if valid, add it to the list of TssMessageTransactions for the target roster.
5. Update the sequence number bit in the vote vector with the results of validation for the TssMessageTransaction.
6. If there are (active roster) threshold number of valid TssMessageTransactions for the target roster, send
   the valid TssMessageTransactions transcript to the TSS Key Manager

`On TssVoteTransaction`:

1. If voting is closed or the vote is a duplicate of an existing vote, do nothing.
2. Add the vote to the total and check if the threshold is met.
3. If the threshold is met, add the roster hash to the voting closed set.

`On PublicSharesAndLedgerId`:

1. If the public shares and ledger id are for the candidate roster,
   1. Sign the ledger ID with the node's private signing key.
   2. Send a `TssVoteTransaction` with the ledger id and the vote vector.

##### Validate TSS Message List

1. For the votes already cast in the vote vector.
   1. Sum all the yes votes into a total count.
   2. If the count is greater than or equal to the threshold in the candidate roster, do nothing and return.
2. For each TSS message in the TSS Message list.
   1. If the TSS Message has a yes entry in the vote vector, do nothing.
   2. Otherwise, validate the message.
   3. If the message is valid, update the vote vector and increment the count of yes votes.
   4. If the count of yes votes is greater than or equal to the threshold in the candidate roster, then send the
      vote vector as a system transaction and exit the validation process.

#### TSS Key Manager

The TSS Key Manager is responsible for generating the public and private keys of the shares for a fully keyed roster.
It receives on input the bundle of TssMessages that generate share keys and produces on output the private shares
for the node, the public shares, and the ledger id.

Internal Elements:

1. Map<RosterHash, Roster> rosters
2. Map<RosterHash, List<TssMessage>> tssMessages
3. Map<RosterHash, Record(RosterHash, List<PairingPrivateKey>, List<PairingPublicKey>, LedgerId)> shares

Inputs:

1. Pair<RosterHash, Roster> (Wire)
2. Pair<RosterHash, List<TssMessage>> (Wire)

Outputs

1. Record(RosterHash, List<PairingPrivateKey>, List<PairingPublicKey>, LedgerId) (Wire)

#### Transaction Resubmitter

The transaction resubmitter is extended to detect which of the new `TssMessageTransaction` and `TssVoteTransaction`
system transactions have not reached consensus because the event containing them has become stale. The transaction
resubmitter will resubmit these transactions to the transaction pool for reprocessing.

### Configuration

The following are new configuration:

1. `tss.maxSharesPerNode` - The maximum number of shares that can be assigned to a node.

### Metrics

The following metrics will be added to the platform:

1. The consensus time when a candidate roster is set.
2. The number of TSS Messages collected for a candidate roster.
3. The number of votes collected for a candidate roster.
4. The time it takes to compute shares from the key material.

### Performance

The `TSS State Manager` execution on the transaction handling thread routes TSS
related system transactions to the relevant components and updates the TSS
related data structures in the state. This routing should be a negligible
impact provided the receiving components receive the messages and handle them
on their own thread(s). The state updates should be simple "crud" operations,
to add, update, and delete from data structures. All other computation on the
transaction handling thread should be basic bookkeeping calculations.

---

## Test Plan

### Unit Tests

Apart from the obvious unit testing of methods and classes, the following scenarios should be unit tested:

- Failure Scenarios
  - Bad TssMessages
  - Failure to reach threshold number of votes
- HAPI Tests
  - A down node during pre-genesis needing to get the key material from post-genesis node.
  - Down nodes during bootstrap and re-keying
  - Multi-release migrations

### Integration Tests

Additional Integration Tests:

- TSS bootstrap for New Networks
- TSS bootstrap for Existing Networks
  - Multiple software upgrades to check transfer of the ledger id.
- Network State Transplant

### Performance Tests

The following needs performance profiling:

1. How long does it take to generate the key material for a new candidate roster as a function of roster size?
2. How much space is being used in the state for all TSS related data structures?

---

## Implementation and Delivery Plan

### Implementation

1. The protobufs for the new system transactions and state data structures.
2. The TSS Message Creator and TSS Message Validator Components (pre-wiring), dependent on (1)
3. The TSS State Manager Component (wired to 2), dependent on (1,2), disabled by feature flag
4. The new startup logic, dependent on (3), behavior determined by feature flag.
5. Any Services Implementation, dependent on (4)
6. HAPI Tests, dependent on (5)
7. Integration Tests, dependent on (5)

### Delivery

1. The TSS-Roster proposal must be delivered in total.
2. The release containing the `tssEncryptionKey` set in the Roster may be delivered with (1).
3. The release containing the TSS Bootstrap Process must be delivered after (2).

---
