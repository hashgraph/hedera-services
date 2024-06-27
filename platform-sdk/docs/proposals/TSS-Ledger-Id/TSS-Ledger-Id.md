# Threshold Signature Scheme (TSS) Ledger ID

---

## Summary

This proposal is for the integration of a threshold signature scheme into the consensus nodes to allow them to create a
private/public key pair for the ledger that can be used to sign blocks and construct block proofs. The private key must
be a secret that no one knows. The public key is known by all and functions as the ledger id. The consensus nodes must
be able to aggregate their individual signatures on a message into a valid ledger signature for the message that is
verifiable by the ledger id. Through signing a block with the ledger private key, we can use Merkle Proofs to
extend the signature to cover the contents of the block and the state changes processed in that round. This forms
the basis of Block Proofs and provides a way for external parties to verify that certain facts are true on the ledger
at a particular point in time. Part of our goal is that users could write EVM smart contracts that can verify the
Block Proofs and make decisions based on their claims.

| Metadata           | Entities                                   | 
|--------------------|--------------------------------------------|
| Designers          | Edward, Cody, Austin, Kore, Anthony, Artem |
| Functional Impacts | Services, DevOps                           |
| Related Proposals  | TSS-Library, TSS-Roster, TSS-Block-Signing |
| HIPS               | N/A                                        |

---

## Purpose and Context

Users of a ledger want to have cryptographic proofs that a statement was true on the ledger at a particular point in
time. Our chosen method for providing these types of proofs is to assign a permanent public/private key pair to the
ledger for use in signing blocks and constructing block proofs. The ledger private key must be a secret that no one
knows. The ledger public key is known by all and functions as the ledger id. While the ledger private key is unknown to
anyone, the consensus nodes must be able to aggregate their individual signatures on a message into a valid ledger
signature for the message that is verifiable by the public key.

The TSS effort has been broken down into four separate proposals: TSS-Library, TSS-Roster, TSS-Ledger-Id, and
TSS-Block-Signing.

1. The `TSS-Library` proposal contains the cryptographic primitives and algorithms needed to implement TSS.
2. The `TSS-Roster` proposal introduces the data structure of a consensus `Roster` to replace the platform's concept of
   an `AddressBook` and modifies the life-cycle for when the platform receives new consensus rosters.
3. This proposal (`TSS-Ledger-Id`) depends on the first two proposals and is for the integration of a threshold
   signature scheme into the consensus node, delivering the ability for the ledger to sign a message with the
   ledger private key.
4. The `TSS-Block-Signing` proposal is everything needed to support the signing of blocks and generation of block
   proofs.

This  `TSS-Ledger-Id` proposal covers changes to the following elements:

* The process of generating the ledger key pair for new networks and existing networks.
* The process for signing a message with the ledger private key.
* The process to transfer the ability of the ledger to sign a message from one set of consensus nodes to another.
* The new state data structures needed to store TSS Key material and the ledger id.
* The new components needed in the framework to support creating and transferring the ledger key.
* The modified startup process to initialize the signing capability.

### Dependencies, Interactions, and Implications

Dependencies on the TSS-Library

* The top-level API in the proposal is defined and can be mocked for initial development.
* The complete implementation of the TSS-Library is required for the TSS-Ledger-Id proposal to be fully implemented.

Dependencies on the TSS-Roster

* The `Roster` API is defined with an AddressBook wrapper for initial development and testing.
* A lot can be done in parallel, but final integration will require that the TSS-Roster proposal be fully implemented.
    * Transferring the ledger key will require the new roster life-cycle be complete and working.

Impacts to Services Team

* TSS-Roster Proposal
    * Services will adopt the new address book / roster life-cycle
    * If the persistent EC Key for nodes is integrated at this stage,
        * The HAPI transactions for add/update of consensus node in the address book must support a required EC public
          key.
* TSS-Ledger-Id Proposal
    * The timing of submitting the candidate roster may need to be adjusted to allow enough time for the candidate
      roster to have enough key material generated.
    * Services will need to know / detect when the candidate roster fails to be adopted due to not having enough key
      material generated.
* TSS-Block-Signing Proposal
    * Services will need to invoke the new ledger signing API to sign a block.
        * This API is asynchronous and will return a future that returns the ledger signature when it is ready.

Impacts to DevOps Team

* Each consensus node will need a new private long term EC key in addition to the existing RSA key.
    * EC Key generation will have to happen before a node can join the network.
* A new node added to an existing network will need to be given a state from an existing node after the network has
  adopted the consensus roster containing the new node.

Implications Of Completion

* The consensus nodes will be able to create ledger signatures for use in block proofs.
* TSS-Block-Signing becomes unblocked for development.

### Requirements

TSS Core Requirements

| Requirement ID | Requirement Description                                                                                                      |
|----------------|------------------------------------------------------------------------------------------------------------------------------|
| TSS-001        | The ledger MUST have a public/private signing key pair that does not change when the consensus roster changes.               |
| TSS-002        | The ledger private key MUST be a secret that nobody knows.                                                                   |
| TSS-003        | The `ledger id` MUST be a public key that everyone knows and able to verify signatures from the ledger private key.          |
| TSS-004        | The ledger signature for a message MUST be produced through a threshold number of consensus nodes signing the message.       |
| TSS-005        | There MUST be a way to bootstrap an existing network's consensus roster with to give the ledger a public/private signing key |
| TSS-006        | There MUST be a way for a new network to generate a public/private signing key for the ledger.                               |
| TSS-007        | There MUST be a way to rotate the ledger's public/private signing key.                                                       |
| TSS-008        | The ledger signature SHOULD be verifiable by an EVM smart contract without excessive cost or time.                           |
| TSS-009        | The TSS implementation SHOULD be able to switch elliptic curves.                                                             |
| TSS-010        | The TSS algorithm SHOULD be able to model consensus weight with high precision.                                              |
| TSS-011        | Minimize the number of steps a Node Operator needs to perform.                                                               |

Block Signing Requirements

| Requirement ID | Requirement Description                               |
|----------------|-------------------------------------------------------|
| TSSBS-001      | The initial block in the block stream MUST be signed. |
| TSSBS-002      | On a new network, the initial block MUST be block 0   |

### Design Decisions

#### New Address Book Life-Cycle

This design proposal will deliver after Dynamic Address Book (DAB) Phase 2 and before DAB Phase 3. In Phase 2 the
address book is updated on software upgrades. In Phase 3 the address book is updated dynamically without restarting
the node. Since the TSS effort requires keying a roster with enough `TssMessages` to generate ledger signatures, a
modified life-cycle is needed. Some of the dynamic work in Phase 3 will be needed. This work is being encapsulated
in the TSS-Roster proposal, which will need to be completed before the TSS-Ledger-Id proposal can be fully implemented.

The relevant Address Book life-cycle changes are the following:

1. The platform only receives a Roster, a subset of the Address Book.
2. The application address book has 3 states: `Active` (AAB), `Candidate` (CAB), and `Future` (FAB).
3. The FAB is updated by every HAPI transaction that is already received in Dynamic Address Book Phase 2.
4. The CAB is a snapshot of the FAB when the app is ready to initiate an address book rotation.
5. The AAB is replaced by the CAB on a software upgrade if the consensus roster derived from the CAB has enough key
   material to generate ledger signatures.
6. The CAB is not adopted and the previous AAB remains the existing AAB after a software upgrade if the CAB does not
   have enough key material to generate ledger signatures.
    * NOTE: These last two condition only applied to `TSS-Ledger-Id`. The `TSS-Roster` proposal will always adopt the
      roster that is set when a software upgrade occurs.

Prior to restart for upgrade, the `candidate` consensus roster will be set in the platform state by the app in such
a way as to mark it clearly as the next consensus roster. This methodology replaces the previous methodology in DAB
Phase 2, which was to write a new `config.txt` to disk. After the `TSS-Roster` proposal has been implemented, the only
need for a file on disk is the genesis address book at the start of a new network.

The core of the life-cycle change for setting candidate rosters in the state iis captured in the `TSS-Roster`
proposal. This Proposal augments the life-cycle with generating the key material for the new roster and the logic for
deciding to adopt the new roster on software upgrade.

![TSS FAB, CAB, AAB Lifecycle](./TSS-FAB-CAB-AAB-Lifecycle.drawio.svg)

#### TSS Algorithm

The threshold signature scheme chosen for our implementation is detailed in [Non-interactive distributed key
generation and key resharing (Groth21)](https://eprint.iacr.org/2021/339). This scheme is able to meet the
core requirements listed above. The rest of this proposal details the design of how to implement this scheme in the
consensus nodes. See the TSS-Library proposal for more details on the Groth21.

The Groth21 algorithm uses [Shamir Secret Sharing](https://en.wikipedia.org/wiki/Shamir%27s_secret_sharing) to hide the
private key of the ledger and distribute shares of it so that a threshold number of node signatures on a message can
generate the ledger signature on the same message. The threshold should be high enough to ensure at least 1 honest
node is required to participate in the signing since a threshold number of nodes can also collude to recover the
ledger private key, if they so choose. For example if the network is designed to tolerate up to 1/3 of the nodes
being dishonest, then the threshold should never be less than 1/3 + 1 of the nodes to ensure that no 1/3 will
collude to recover the ledger private key.

The Groth21 algorithm requires specific Elliptic Curve (EC)s with the ability to produce bilinear pairings. Each
node will need its own long-term EC key pair on the curve for use in the groth21 TSS algorithm. Each node receives
some number of shares in proportion to the consensus weight assigned to it. Each share is an EC key pair on the
same curve. All public keys of the shares are known, and aggregating a threshold number of share public keys will
produce the ledger id. Only the node has access to the private key of its own shares. Each node must keep their
private keys to themselves since a threshold number of share private keys can aggregate to recover the ledger
private key. An aggregate number of signatures on the same message from the share private keys will aggregate to
recover the ledger signature on the message.

Transferring the ability to generate ledger signatures from one set of consensus nodes to another is done by having
each node generate a `TssMessage` for each share that fractures the share into a number of subshares or `shares of
shares`. Each `share of a share` is encrypted with the public key of the node in the next consensus roster that it
belongs to so that only the intended node can use the `share of a share` to recover that node's share's private key.  
The collection of `TssMessages` generated from nodes in the previous consensus roster forms the `key material` for
the new consensus roster. To bootstrap a network and generate the ledger private and public keys, each node creates
a random share for themselves and generates a `TssMessage` containing `shares of shares` for the total number of shares
needed in the consensus roster. The use of random shares at the outset creates a random ledger private key that nobody
knows.

![TSS re-keying next consensus roster](./Groth21-re-keying-shares-of-shares.drawio.svg)

This process of re-keying the next consensus roster during an address book change takes an asynchronous amount of
time through multiple rounds of consensus to complete. This requires saving incremental progress in the state to ensure
that the process can resume from any point if a node or the network restarts. Switching to the next consensus roster
cannot happen until that roster has enough nodes able to recover a threshold number of shares so that an aggregation of
node signatures can generate the ledger signature.

##### Elliptic Curve Decisions

Our first implementation of Groth21 will use the ALT_BN128 elliptic curve which is in use and verifiable by EVMs.  
If and when the Ethereum ecosystem adopts BLS12_381, we will likely switch over to that more secure curve.

##### New Elliptic Curve Node Keys

Each node will need a new long-term EC key pair in addition to the existing RSA key pair. The EC key pair will be
used in the Groth21 algorithm to generate and decrypt `TSS-Messages`. These new EC Keys will not be used for
signing messages, only for generating the shares. It is the share keys that are used to sign messages. The public
keys of these long-term node specific EC keys must be in the address book.

##### Groth21 Drawbacks

The Groth21 algorithm is not able to model arbitrary precision proportions of weight assigned to nodes in the
network. For example if each node in a 4 node network received 1 share, then every share has a weight of 1/4.
If there are a total of N shares, then the distribution of weight can only be modeled in increments of (1/N) * total
weight. The cost of re-keying the network in an address book change is quadratic in the number of shares. (See the
picture of TssMessages above.) This forces us to pick a number of total shares with a max value in the thousands.
This modeling of weight uses small integer precision.

##### Calculating Number of Shares from Weight

Given that we have a low resolution for modeling weight, the number of shares assigned to each node will be
calculated as follows:

* Let `N` be the max number of shares that any node can have.
* Let `maxweight` be the max weight assigned to any node.
* Let `weight(node)` be the weight assigned to the node.
* Let `numShares(node)` be the number of shares assigned to the node.
* then `numShares(node) = ceiling(N * weight(node) / maxweight)` where the operations are floating point.

##### Threshold for recovery of Signatures and Ledger Id.

Our public claim is that we can tolerate up to 1/3 of the stake assigned to malicious nodes. Our
threshold for recovery of ledger signatures and the ledger id must be greater than 1/3 of the total number of shares.
The integer threshold should be `ceiling((N + 1) / 3)` where `N` is the total number of shares.

##### TSS Algorithm - Alternatives Considered

The list of options considered here were based off of prototypes developed by Rohit Sinha, the Swirlds Labs
cryptography expert. The Groth21 algorithm was chosen because it was efficient for use in smart contract
verification, and we could assign a multiplicity of shares to nodes to get close enough in modeling the
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
recursive proof proved too expensive for EVM smart contracts.

#### TSS Genesis Process

The TSS Genesis process is the process of generating the ledger public/private key pair for a new network or an existing
network. There are several options to consider in the TSS genesis process:

1. Do we allow block number and round number to be out of sync?
2. Do we need the initial block produced to be signed by the ledger id?
3. For new networks, Do we need to produce a signed block 0?
4. For existing networks, does the TSS genesis roster sign blocks prior to its existence?

##### For New Networks

There will be a pre-genesis phase where the genesis roster is keyed and a random ledger id is created. This will
happen through running the platform without application transactions. After the platform keys the roster, the
platform restarts itself with a copy of the genesis state and adding to it the TSS key material and ledger
id. After platform restart, application transactions are accepted and the platform starts building the new hashgraph
from round 0.

While this solution does not have the history of setting up the ledger id in the hashgraph after platform restart,
the node can cryptographically verify that the setup process took place correctly through using the TSS key material to
re-derive the ledger id.

This process ensures that block 0 containing application transactions is signed by the ledger and verifiable with
the ledger id.

The process of the platform restarting itself after performing a pre-genesis keying phase is new startup logic.  
This should not involve an interruption or restart to the containing process or application.

##### For Existing Networks

Given the requirement of TSSBS-001, the setup of the TSS Key Material must happen before the software upgrade that
enables block streams. In order to reduce risk and density of complex software changes, giving the nodes
their long term EC keys and changing the roster life-cycle through the TSS-Roster effort should be completed and
delivered in a software release prior to the code that will key the first candidate roster.

For example, if Block Streams ships in release 0.7, then TSS-Ledger-Id should ship in release 0.6, and TSS-Roster
along with setting the EC keys on nodes should ship in release 0.5.

The setup of the first key material will be observable in the record stream prior to the Block Streams going live,
but this observability is not consequential because what matters is the cryptographic verification that the TSS
material can generate the ledger key.

##### TSS Genesis Process - Alternatives Considered

| N | Option                                                                                                        | 1-1 round to block                   | round # == block #                                   | Ledger signs initial block           | Ledger signs block 0                 | TSS keying in hashgraph history      | TSS keying in block history          | Minimal DevOps Impact                | Implementation Complexity                 | Notes                                                                              |
|---|---------------------------------------------------------------------------------------------------------------|--------------------------------------|------------------------------------------------------|--------------------------------------|--------------------------------------|--------------------------------------|--------------------------------------|--------------------------------------|-------------------------------------------|------------------------------------------------------------------------------------|
| 1 | TSS Setup in block history, initial block > 0 and not signed                                                  | <span style="color:green">YES</span> | <span style="color:green">YES</span>                 | <span style="color:red">NO</span>    | <span style="color:red">NO</span>    | <span style="color:green">YES</span> | <span style="color:green">YES</span> | <span style="color:green">YES</span> | <span style="color:green">LOW</span>      | Mainnet if Block Streams ships before Block Proofs                                 |
| 2 | TSS Setup not in block history, initial block > 0 and is signed                                               | <span style="color:green">YES</span> | <span style="color:green">YES</span>                 | <span style="color:green">YES</span> | <span style="color:red">NO</span>    | <span style="color:green">YES</span> | <span style="color:red">NO</span>    | <span style="color:green">YES</span> | <span style="color:green">LOW</span>      | Mainnet if Block Streams ships with Block Proofs                                   |
| 3 | TSS Setup not in block history, initial block == 0 and is signed                                              | <span style="color:green">YES</span> | <br/><span style="color:red">NO</span>, fixed offset | <span style="color:green">YES</span> | <span style="color:red">NO</span>    | <span style="color:green">YES</span> | <span style="color:red">NO</span>    | <span style="color:green">YES</span> | <span style="color:orange">LOW-MED</span> | Cognitive burden to reason about block # and round # discrepancy.                  |
| 4 | TSS Setup in block history, initial block > 0, covers all prior rounds, and is signed                         | <span style="color:red">NO</span>    | <span style="color:green">YES</span>                 | <span style="color:green">YES</span> | <span style="color:red">NO</span>    | <span style="color:green">YES</span> | <span style="color:green">YES</span> | <span style="color:green">YES</span> | <span style="color:orange">MED</span>     |                                                                                    |
| 5 | TSS Setup in block history, initial block == 0, covers all prior rounds, and is signed                        | <span style="color:red">NO</span>    | <br/><span style="color:red">NO</span>, fixed offset | <span style="color:green">YES</span> | <span style="color:red">NO</span>    | <span style="color:green">YES</span> | <span style="color:green">YES</span> | <span style="color:green">YES</span> | <span style="color:orange">MED</span>     | Cognitive burden to reason about block # and round # discrepancy.                  |
| 6 | Retrospectively sign the initial blocks with TSS once it becomes available.                                   | <span style="color:green">YES</span> | <span style="color:green">YES</span>                 | <span style="color:green">YES</span> | <span style="color:green">YES</span> | <span style="color:green">YES</span> | <span style="color:green">YES</span> | <span style="color:green">YES</span> | <span style="color:red">HIGH</span>       | TSS Genesis Roster signs older blocks.  This is compatible with all other options. |
| 7 | Pre-Genesis: Separate app with genesis roster creates key material in state before network officially starts. | <span style="color:green">YES</span> | <span style="color:green">YES</span>                 | <span style="color:green">YES</span> | <span style="color:green">YES</span> | <span style="color:red">NO</span>    | <span style="color:red">NO</span>    | <span style="color:red">NO</span>    | <span style="color:red">HIGH</span>       | Applies to new networks only.  Use Option 1 or 2 for existing networks.            |
| 8 | Instead of separate app, detect genesis, key the state, network restart from round 0 with keyed state.        | <span style="color:green">YES</span> | <span style="color:green">YES</span>                 | <span style="color:green">YES</span> | <span style="color:green">YES</span> | <span style="color:red">NO</span>    | <span style="color:red">NO</span>    | <span style="color:green">YES</span> | <span style="color:red">HIGH</span>       | Applies to new networks only.  Use Option 1 or 2 for existing networks.            | 

### Goals

The following capabilities are the goals of this proposal:

1. `Ledger Signing API` - Given a message, the consensus nodes sign the message with shares and aggregate the signatures
   into a ledger signature after they come to consensus.
2. `TSS Genesis on Existing Network` - Able to setup an existing network with a ledger public/private key pair.
3. `TSS Genesis for New Network` - Able to setup a new network with a ledger public/private key pair.
4. `Keying A New Roster` - Able to transfer the ability to sign a message with the ledger private key from one set of
   consensus nodes to another.

### Non-Goals

The following are not goals of this proposal:

1. Achieving a fully dynamic address book life-cycle.
2. Block Proofs or the signing of blocks.
3. Verification of signed messages beyond the consensus node.
    * It is not within the scope of this proposal and effort to develop, test, and deploy a smart contract that can
      verify a block proof. It is expected that mathematics works and that our use of the ALT-BN128 curve will
      dovetail with the
      [precompiles that are available in the EVM](https://github.com/ethereum/EIPs/blob/master/EIPS/eip-1829.md).

---

## Changes

Integrating a Threshold Signature Scheme into the consensus node requires significant changes to the startup
process for a node and the process of changing the consensus roster. These changes are outline in more detail in
the following sections, but a brief summary is presented here.

New Network TSS Genesis Process

* The platform will accept a genesis roster and check the state for the TSS key material.
* If the TSS key material is not present, the platform will generate the key material and ledger id.
* The platform will restart itself from round 0 with the new key material and ledger id in the state.
  Existing Network Genesis
*

### Architecture and/or Components

The architecture is presented first through the data structures that are stored in the state and then through the
components that interact with the data structures.

#### State Datastructures

The state needs to store the relevant ledger id, consensus rosters, and key material.

##### Ledger Id

The `ledgerId` is the public ledger key able to verify ledger signatures. It is used by both the application and the
platform. This value should not change during address book changes and its value does not change unless the network
goes through a another TSS Genesis process. Since its value is not expected to change, storing it in a singleton
Merkle Leaf by itself is appropriate.

##### Consensus Rosters

It is expected that the TSS-Roster proposal has introduced a 3rd roster in the state called the `Candidate Roster`
in addition to the `Active Roster` and the `Previous Roster`. These rosters are stored in the state as their own
singleton.

This proposal extends the roster data format in two ways:

1. Each `RosterEntry` has a field for the number of shares allocated to the node. This value will not get big and
   can fit in a byte or short.
2. Each `RosterEntry` has a new long term public EC key called a `tssEcKey` that is needed in the Groth21 algorithm to
   key rosters.

##### Key Material (TSS Data Map)

The TSS Data Map is a singleton Merkle Leaf that is a combined map of all the key material for all rosters that are
tracked and the votes by nodes that have validated the key material.

Keys in the TSS Data Map have the following structure: (KeyType, RosterHash, SequenceNumber).

1. KeyType - An enum that is one of TSS_MESSAGE or TSS_VOTE.
2. RosterHash - The hash of the consensus roster that the message or vote is related to.
3. SequenceNumber - The order in which the message or vote came to consensus.

The value associated with a TSS_MESSAGE key is the pair (ShareId, TssMessage)

1. ShareId - The id of the share that the message is related to.
2. TssMessage - the raw bytes of the TssMessage

The value associated with a TSS_VOTE key is a bit vector with the following interpretation

* The order of bits from the least significant bit to the most significant bit corresponds to the numeric order of
  share ids
  from least to greatest.
* If a bit is set to 1, then the TssMessage for the corresponding ShareId was valid and contributed to a successful
  reconstruction of the ledger id.
* If a bit is set to 0, then the TssMessage for the corresponding ShareId was either invalid, was not received, or
  was not used in the reconstruction of the ledger id.

Lifecycle Invariants

1. Inserts should only be made for candidate rosters that are going through the keying process.
2. When a roster is no longer stored in the state, the corresponding key material should be removed.
3. Every roster that has become active after the TSS Genesis process must have sufficient key material to be
   able to recover the ledger id.

#### New or Updated Components

The following are new components in the system:

1. TSS State Manager (Not Wired)
2. TSS Message Creator (Wired)
3. TSS Message Validator (Wired)
4. TSS Key Manager (Wired)
5. TSS Signing Manager (Wired)
6. Roster Initializer (Not Wired)

The following components are removed from or replaced in the system:

1. AddressBookInitializer

##### TSS State Manager

The TSS state manager is executed on the transaction handling thread and is capable of reading and writing the state
during round handling. The logic will be encapsulated in a class with a well-defined API, but this will not be a fully
fledged wiring component outside the consensus round handler.

Responsibilities:

1. Handle `TssMessage` system transactions
    - handled in consensus order
    - insert into the TSS data map if it is legal to do so
        - don’t insert multiple messages for the same node
        - don’t insert any messages if voting window has closed
    - if inserted into the TSS data map, ensure that the message is forwarded to the TSS Message Validator
    - if it is a self TSS message, forward the message to the TSS Message creator.
2. Handle `TssVote` system transactions
    - handled in consensus order
    - insert into TSS data map if it is legal to do so
        - don’t insert multiple votes for the same node
        - don’t insert once voting is closed
    - after insertion, re-tally votes and decide if voting is now closed
    - if voting is closed as the result of a new vote,
        - if the “no” votes win, then the candidate roster will never be adopted
        - if the “yes” votes win, the candidate roster is now officially confirmed, and will be adopted at the next
          upgrade boundary
3. Detect when a new candidate roster is set.
    - update the pending roster hash in the platform state
    - inform the TSS message creator that there is a new candidate roster
    - inform the TSS message validator that there is a new candidate roster
4. Update Metrics
    - indicate whether there is a candidate roster
    - the current number of TSS messages collected for the candidate roster
    - the current number of votes collected for the candidate roster
        - keep different metrics both for “no” votes and “yes” votes
5. On First Round After Restart (After PCES Replay and Reconnect)
    - if there is no candidate roster, take no action
    - if voting is closed, take no action
    - if this node’s TSSMessages are not recorded in the TSSData map, send a message to the TSS message creator (so
      it can resubmit the node’s TSS message)
    - if this node’s vote is not recorded in the TSS data map, inform the TSS message validator that we still need to do
      validation.

##### TSS Message Creator

The TSS message creator is responsible for generating a node’s TSSMessages. This runs as its own asynchronous thread
since this may be a computationally expensive operation.

Internal Elements:

1. TSS Message Manager
2. active roster field
3. candidate roster field

Inputs:

1. Active Roster
2. Candidate Roster
3. Self TSS Message
4. Voting Closed Notification

Input Invariants:

1. Self TSS Messages must be received after the candidate roster is set.
2. The candidate roster is not set if the voting is already closed.

`On Active Roster`:

1. Set this roster as the active roster.
    1. If the candidate roster is set and the candidate roster is not the active roster, then (re)start the TSS Message
       Manager with the active roster and candidate roster.
    2. If the candidate roster is set and the candidate roster is the active roster, then clear the candidate roster,
       clear the TTS Message List, and stop the TSS Message Manager

`On Candidate Roster`:

1. If the internal candidate roster is set and equal to the input candidate roster, do nothing.
2. else, set the internal candidate roster.
3. if the active roster is set, (re)start the TSS Message Manager with the active roster and candidate roster.

`On Self TSS Message`:

1. If the TSS Message Manager is active, forward the message to the TSS Message Manager.
2. else log an error indicating that the TSS Message was received before the candidate roster was set.

`On Voting Closed Notification`:

1. Stop the TSS Message Manager.

###### TSS Message Manager

1. Keep a list of Self TSS Messages that have been received.
2. `On Start`:
    1. Wait the configured time period to receive any previously sent Self TSS Messages (relevant for restarts)
    2. For each share assigned to this node in the active roster that we do not have a Self TSS message for, generate a
       TSS Message from the share for the candidate roster and submit a signed system transaction with the TSS Message.
    3. If a TSS Message does not come back through consensus within a configured time period, resubmit the TSS Message.

3. `On TSS Message`:
    1. verify the signature on the self TSS Message, log an error if the signature is invalid.
    2. stop resending the TSS Message that matches the one we received and added to the internal list.

##### TSS Message Validator

The TSS message validator is responsible for validating TSS messages and for submitting votes as to the validity of the
candidate roster. The TSS message validator gets its input from the TSS state manager in the form of a candidate roster
that we want to adopt, and as a sequence of TSS messages that have reached consensus.

Internal Elements:

1. active roster field
2. candidate roster field
3. TSS Message list
4. vote bit vector

Inputs:

1. Active Roster
2. Candidate Roster
3. TSS Message

Input Invariants

11. TSS Messages for the candidate roster must be received after the candidate roster is set.

`On Active Roster`:

1. Set the active roster.
2. If the candidate roster is set and equals active roster, clear the candidate roster and the TSS Message List.
3. else, Validate the TSS Message List

`On Candidate Roster`:

1. If the input candidate roster is equal to the active roster, do nothing.
2. If the input candidate roster is equal to the internal candidate roster, do nothing.
3. Set the internal candidate roster, clear the TSS Message List.

`On TSS Message`:

1. If the vote bit vector is passing for a yes vote, do nothing.
2. If the vote bit vector has already recorded a yes or no for the share in the TSS Message, report duplicate in
   metrics, do nothing else.
3. append the TSS Message to the TSS Message List.
4. If the active and candidate rosters are set, validate the TSS Message List

###### Validate TSS Message List

1. For the votes already cast in the vote vector
    1. sum all the yes votes into a total count.
    2. If the count is greater than or equal to the threshold in the candidate roster, do nothing and return.
2. For each TSS message in the TSS Message list
    1. If the TSS Message has a yes entry in the vote vector, do nothing.
    2. otherwise, validate the message.
    3. If the message is valid, update the vote vector and increment the count of yes votes.
    4. If the count of yes votes is greater than or equal to the threshold in the candidate roster, then send the
       vote vector as a system transaction and exit the validation process.

##### TSS Key Manager

TODO:  Separate out the computation of the shares into the TSS Key Manager.

##### TSS Signing Manager

The TSS Signing Manager is responsible for computing a node's private shares from the TSS Messages and for
generating ledger signatures on messages. The Signing manager operates on its own thread.

Public API:

1. Future<PairingSignature> signMessage(byte[] message) throws NotReadyException

Internal Elements:

1. active roster
2. active private shares
3. active public shares
4. current round
5. latest roster round
6. Map<round, roster> roundRosterMap
7. Map<round, Pair(private shares, public shares)> roundSharesMap
8. Map<Message Hash, List<PairingSignature>> messageSignaturesMap
9. Map<Message Hash, Future<PairingSignature>> messageSignatureFuturesMap

Inputs:

1. roster key material: Triple(round, active roster, TSS Messages for active roster)
2. TssSignatureMessage
3. EventWindow

Input Invariants:

1. TssSignatureMessages received must be for previous signMessage(byte[] message) calls.

On Roster Key Material:

1. update the roundRosterMap with the roster.
2. computeShares(TSS Messages)

On TssSignatureMessage:

1. get the hash of the message the signature is for.
2. if the message hash is not a key in the messageSignaturesMap, do nothing.

The TSS key manager is special logic that only runs when the system starts up.  (When DAB phase 3 is implemented,
this will happen whenever the active consensus roster is updated.)

- if we are not upgrading, then the TSS key manager is responsible for computing this node’s private key and the
  network’s public key, and for distributing that data to components that need it.
    - if we don’t have TSS message validity completed when we start up, we have no choice but to block until it has been
      completed
- if we are upgrading and the candidate roster has sufficient “yes” votes, the TSS key manager is responsible for
  computing the same information but for the candidate address book (which becomes the active address book in the first
  round following the upgrade)
    - similar to the restart case, we must block if we have not yet validated TSS messages

##### Roster Initializer

### Core Behaviors

Describe any new or modified behavior. What are the new or modified algorithms and protocols? Include any diagrams that
help explain the behavior.

Remove this section if not applicable.

#### TSS Genesis for New Networks

#### TSS Genesis for Existing Networks

#### Keying A New Roster

#### Ledger Signing API

### Public API

#### Ledger Signing API

#### Ledger Signature Protobuf

#### State Data Structures

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