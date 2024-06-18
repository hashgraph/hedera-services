# Threshold Signature Scheme (TSS) Ledger ID

---

## Summary

This proposal is for the integration of a threshold signature scheme into the consensus nodes to allow them to create a
private/public key pair for the ledger that can be used to sign blocks and construct block proofs. The private key must
be a secret that no one knows. The public key is known by all and functions as the ledger id. The consensus nodes must
be able to aggregate their individual signatures on a message into a valid ledger signature for the message that is
verifiable by the public key.

| Metadata           | Entities                                   | 
|--------------------|--------------------------------------------|
| Designers          | Edward, Cody, Austin, Kore, Anthony, Artem |
| Functional Impacts | Services, DevOps                           |
| Related Proposals  | TSS-Library, TSS-Block-Signing, TSS-Roster |
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
3. The `TSS-Ledger-Id` proposal depends on the other first two proposals is for the integration of a threshold signature
   scheme into the consensus node, delivering the ability for the ledger to sign a message with the ledger private key.
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
    * The Roster API must be expanded to include the number of shares each node is assigned.
* A lot can be done in parallel, but final integration will require that the TSS-Roster proposal be fully implemented.
    * Transferring the ledger key will require the new roster life-cycle be complete and working.

Impacts to Services Team

* The app will need to specify how many shares are assigned to each node when it sets a new consensus roster.
* Services will need to invoke the new ledger signing API to sign a block.
    * This API is asynchronous and will return a future that returns the ledger signature when it is ready.
* The HAPI transactions for add/update of consensus node in the address book must support a required EC public key.

Impacts to DevOps Team

* Each consensus node will need a new private long term EC key in addition to the existing RSA key.
    * EC Key generation will have to happen before a node can join the network.
* A new node added to an existing network will need to be given a state from an existing node after the network has
  adopted the consensus roster containing the new node.

Implications Of Completion

* The consensus nodes will be able to create ledger signatures for use in block proofs.

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

### Design Decisions

The threshold signature scheme chosen for our implementation is detailed in https://eprint.iacr.org/2021/339. This
scheme is able to meet the core requirements listed above. '

Describe the decisions made and the reasons why.

#### Alternatives Considered

Describe any alternatives considered and why they were not chosen.

If possible, provide a table illustrating the options, evaluation criteria, and scores that factored into the decision.


---

## Changes

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