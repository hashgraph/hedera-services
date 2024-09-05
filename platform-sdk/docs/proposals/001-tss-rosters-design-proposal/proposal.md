# TSS - Roster Proposal

---

## Summary

This proposal outlines the design specification of rosters to support Threshold Signature Scheme (TSS) block signing.
It details the roster's lifecycle, data storage, API, DevOps workflow, and necessary changes to associated
components within the platform.

|      Metadata      |                   Entities                    |
|--------------------|-----------------------------------------------|
| Designers          | Kore, Anthony, Ed, Austin                     |
| Functional Impacts | TSS, Address Book, Reconnect,                 |
| Related Proposals  | TSS-Library, TSS-Ledger-Id, TSS-Block-Signing |
| HIPS               | N/A                                           |

## Purpose and Context

The introduction of the Threshold Signature Scheme (TSS) requires a new mechanism for managing node participation in
consensus and block signing. The Roster, a subset of the address book, will provide the data structure and API for this
mechanism. This proposal provides a specification for the behavior of rosters starting from their creation from the
Address Book to their terminal state. A roster reaches a terminal state when it is either adopted by the platform or
replaced by a new roster. The candidate roster will always get adopted on the next software upgrade.

The Hedera App (henceforth referred to as 'App') will maintain a version of the Address Book as a dynamic list that
reflects the desired future state of the network's nodes. This dynamic list of Addresses will be continuously
updated by HAPI transactions, such as those that create, update, or delete nodes. At some point when the App decides
it's time to begin work on adopting a new address book (scope beyond this proposal), it will create a `Roster`
object with information from the Address Book and pass it to the platform. The mechanism for doing so is detailed below.

## What this proposal does not address

## Requirements

- Immutability: The roster hash will be a critical piece of data. Rosters must be immutable to protect the integrity
  of its computed hash.
- Efficient Storage: Roster data should be stored efficiently in the state to minimize overhead and unnecessary data
  copying.
- Clear API: A well-defined API should be provided for the App to create and submit candidate rosters to the platform,
  and for the platform to manage the lifecycle of submitted rosters.
- Components update: Platform components that currently rely on the use of the Address book (such as Reconnect, Network,
  Event Validation etc.) must be adapted to use the new `Roster` object instead.
- Address book paradigm discontinued within the platform codebase in favor of `Roster`s

## Architecture

###### Roster Lifecycle

![](TSS%20Roster%20Lifecycle.drawio.svg)

### Roster Public API

#### Setting the Candidate Roster

A new method will be added to the platform state to allow the App to submit a Candidate Roster to the platform:

```java
//in PlaformState

void setCandidateRoster(@NonNull final Roster candidateRoster);
```

The steps to validate and add the Roster to the state will be executed within the same thread that submits the request.
An exception will be thrown if the Roster is invalid. The durability of the submitted Roster is ensured via storing
it in the state.

The existing `PlatformState` class is in the middle of a refactor, and in the long term, may cease to exist. However,
its replacement will continue to have this new API to set a candidate roster.

## Data Structure

The App is already responsible for managing the address book. We propose that it continues to do so, and when it
deems necessary (such as when it receives a HAPI transaction), creates a candidate Roster object and passes it to
the platform via the new API. The platform will then validate the roster and store it in the state.

### Protobuf

#### Roster

The protobuf definition for the Roster will look as follows:

```proto
/**
 * A single roster in the network state.
 * <p>
 * The roster SHALL be a list of `RosterEntry` objects.
 */
message Roster {

    /**
     * List of roster entries, one per consensus node.
     * <p>
     * This list SHALL contain roster entries specified in order of ascending node id.
     * This list SHALL NOT be empty.<br/>
     */
    repeated RosterEntry rosterEntries = 1;
}
```

#### RosterEntry

A roster entry will look as follows:

```proto
/**
 * A single roster entry in the network state.
 *
 * Each roster entry in SHALL encapsulate the elements required
 * to manage node participation in the Threshold Signature Scheme (TSS).<br/>
 * All fields are REQUIRED.
 */
message RosterEntry {

    /**
     * A consensus node identifier.
     * <p>
     * Node identifiers SHALL be unique _within_ a ledger,
     * and MAY NOT be repeated _between_ shards and realms.
     */
    uint64 node_id = 1;

    /**
     * A consensus weight.
     * <p>
     * Each node SHALL have a weight of zero or more in consensus calculations.<br/>
     * The sum of the weights of all nodes in the roster SHALL form the total weight of the system,<br/>
     * and each node's individual weight SHALL be proportional to that sum.<br/>
     */
    uint64 weight = 2;

    /**
     * An RSA public certificate used for signing gossip events.
     * <p>
     * This value SHALL be a certificate of a type permitted for gossip
     * signatures.<br/>
     * This value SHALL be the DER encoding of the certificate presented.<br/>
     * This field is REQUIRED and MUST NOT be empty.
     */
    bytes gossip_ca_certificate = 3;

    /**
     * An ALT_BN128 elliptic curve public encryption key.<br/>
     * <p>
     * The elliptic curve type may change in the future. For example, <br/>
     * if the Ethereum ecosystem creates precompiles for BLS12_381,
     * we may switch to that curve.
     * <p>
     * This value SHALL be specified according to EIP-196 and EIP-197 standards, <br/>
     * See https://eips.ethereum.org/EIPS/eip-196#encoding and <br/>
     * https://eips.ethereum.org/EIPS/eip-197#encoding
     * <p>
     * This field is OPTIONAL (that is, it can initially be null)
     * but once set, it can no longer be null.
     */
    bytes tss_encryption_key = 4;

    /**
     * A list of service endpoints for gossip.
     * <p>
     * These endpoints SHALL represent the published endpoints to which other
     * consensus nodes may _gossip_ transactions.<br/>
     * If the network configuration value `gossipFqdnRestricted` is set, then
     * all endpoints in this list SHALL supply only IP address.<br/>
     * If the network configuration value `gossipFqdnRestricted` is _not_ set,
     * then endpoints in this list MAY supply either IP address or FQDN, but
     * SHALL NOT supply both values for the same endpoint.<br/>
     * This list SHALL NOT be empty.<br/>
     */
    repeated proto.ServiceEndpoint gossip_endpoint = 5;
}
```

#### Roster Map

A States API map `Roster Map` will be introduced. It will contain keys which will be the hash of the roster (of
type `byte`) and values of type `Roster`.
This map will store all candidate and active rosters. It will be an implementation of `ReadableKVState<>`
and `WriteableKVState<>` interfaces.

The `byte` key of the Roster Map will be of the widely used type `com.hedera.pbj.runtime.io.buffer.Bytes`.

#### Roster State

The `RosterState` protobuf is defined as follows.

```proto
/**
 * The current state of platform rosters.<br/>
 * This message stores a roster data for the platform in network state.
 *
 * The roster state SHALL encapsulate the incoming candidate roster's hash,<br/>
 * and a list of pairs of round number and active roster hash.<br/>
 * This data SHALL be used to track round numbers and the rosters used in determining the consensus.<br/>
 */
message RosterState {

    /**
     * The SHA-384 hash of a candidate roster.
     * <p>
     * This is the hash of the roster that is currently being considered
     * for adoption.
     * A Node SHALL NOT, ever, have more than one candidate roster
     * at the same time.
     */
    byte candidate_roster_hash = 1;

    /**
     * A list of round numbers and roster hashes.
     * The round number indicates the round in which the corresponding roster became active
     * <p>
     * This list SHALL be ordered by round numbers.<br/>
     * This list MUST be deterministically ordered.
     */
    repeated RoundRosterPair round_roster_pairs = 2;
}
```

#### RoundRosterPair

A `RoundRosterPair` will be defined as follows:

```proto
/**
 * A pair of round number and active roster hash.
 * <p>
 * This message SHALL encapsulate the round number and the hash of the
 * active roster used for that round.
 */
message RoundRosterPair {

    /**
     * The round number.
     * <p>
     * This value SHALL be the round number of the consensus round in which this roster became active.
     */
    uint64 round_number = 1;

    /**
     * The SHA-384 hash of the active roster for the given round number.
     * <p>
     * This value SHALL be the hash of the active roster used for the round.
     */
    byte active_roster_hash = 2;
}
```

It is noteworthy that the roster must be immutable to guarantee the integrity of the computed hash. This map of
rosters will typically contain the current Active Roster, an optional previously Active Roster, and an optional
current Candidate Roster. Insertion of rosters will be controlled by ensuring that the acceptance of a new Candidate
Roster invalidates and removes the current candidate roster. Therefore, the map is expected to have a maximum size
of three elements.

The new `RosterState` will store the `candidateRosterHash` and a list `roundRosterPairs`
of pairs of round numbers and the active roster that was used for that round. The `roundRosterPairs` list will be
updated only when the active roster changes. In the vast majority of cases, the list will contain only one element,
the current active roster.

Immediately following the adoption of a candidate roster, this list will have two elements — the previous active roster
and the current active roster (which was the candidate roster). The choice of a list is suitable here as it is much
cheaper to iterate over 2-3 elements comparing their `long` round numbers than it is to do a hash lookup. When we
reach full Dynamic Address Book, this design choice may need to be revisited.

If a `candidateRosterHash` hash entry already exist (hash collisions) in the map of Rosters, it will be discarded.
That is, setting a candidate roster is an idempotent operation. One benefit of this indirection (using a Map
instead of a Singleton) is that it avoids moving data around in the merkle state. Another benefit is that adoption
trigger becomes straightforward (App sets the roster) with delineated responsibilities between the App and the platform.

### Roster Validity

A Roster is considered valid if it satisfies the following conditions:

1. The roster must have at least one RosterEntry.
2. At least one RosterEntry/ies must have a non-zero weight.
3. All RosterEntry/ies must have a valid gossip_ca_certificate.
4. All RosterEntry/ies must have a valid tss_encryption_key.
5. All RosterEntry/ies must have at least one gossip Endpoint.
6. All ServiceEndpoint/s must have a valid IP address or domain name (mutually exclusive), and port.
7. The roster must have a unique NodeId for each RosterEntry.

On the submission of a new Candidate Roster, the platform will validate the roster against these conditions. Note
that a constructed `Roster` can be valid, but not accepted by the platform. For example, if a new candidate roster
is set via the API, but its hash evaluates to the hash of the existing candidate roster, the new roster will be
discarded. That is, the operation has no effect.

## Data storage

### State

All the data related to Active and Candidate Roster will be stored in the State. Two new objects will be added to
the state:

1. A `RosterState` singleton object that will store the current candidate roster hash and a list of round numbers
   and hashes of the active roster used to sign each round.
2. A map of rosters, keyed by roster’s hash - ```Map<Hash<Roster>, Roster>```. All candidate and active rosters will be
   stored here.
   When a new candidate roster is received, it will be stored in this map, with the `RosterState`'s current candidate
   roster hash updated.

### On Disk

There will continue to be components used in creating the Roster - such as the private EC (elliptic curve) key, the RSA
private key and the signing X509Certificate certificate - that will be stored on disk. However, it is at the
discretion of the Services team to manage the lifecycle of these files, and not the platform. There will not be any
other separate artifacts from this proposal stored directly on disk.

## Services and DevOps changes (Inversion of control)

The current startup procedure will be altered as follows:

- The `config.txt` file will no longer be used by the platform for storing the address book in existing networks. It
  will no longer be used by the platform code
- Designating `config.txt` introduces inversion of control. It will be at the exclusive prerogative of Services (the
  App) going forward. Platform will no longer be responsible for it, and all the platform code that builds an
  AddressBook from `config.txt` will be removed or refactored and moved into the Services codebase.

The current network Transplant procedure is manual. DevOps is given a State and `config.txt` file on disk. This is
then followed by a config-only upgrade to adopt the `config.txt` file using that state. This will change to the
use of a Genesis Roster or Override Roster. The new startup sequence will be determined by the presence of a Genesis
Roster, State, or Override Roster as described in the following sections.

### The new Startup Behaviour

![](TSS%20Roster%20Lifecycle-Proposed%20Startup%20Behavior.drawio.svg)

#### Genesis Roster

A `Genesis Roster` is an optional Roster that DevOps may provide for the explicit purpose of starting a Genesis Network
Process. It is essential to distinguish the Genesis Roster from the Candidate, Active, or Override rosters. The
Genesis Roster is a special roster used for the sole purpose of bootstrapping a brand-new Genesis network; one in
which there is no existing State, and the round number begins at 1. However, its structure is exactly the same as
the Active or Candidate Rosters.

The equivalent of the Genesis Roster in the current DevOps flow is the `config.txt` file. DevOps and Services may
choose to create the Genesis Roster from the `config.txt` file or some other mechanism. The trigger for this
workflow will be the presence of a Genesis Roster at start-up with no State on disk.

A new method will be added to the `PlatformBuilder` that will be used by the App to set the Genesis Roster.
In most cases, there will be no need to call the method on `PlatformBuilder` to set the Genesis Roster except in the
events we need to bootstrap a Genesis Network.

```java
//in PlatformBuilder

/*
  Set the Genesis Roster for the network.
  <p>
  This method is used to set the optional Genesis Roster for the network.
  The Genesis Roster is a special roster used for the sole-purpose of bootstrapping a network.
  The Genesis Roster, if set, is immutable and will be used to start a Genesis network Process or Network Transplant process.
  <p>
  @param genesisRoster the Genesis Roster for the network.
 */
PlatformBuilder withGenesisRoster(@NonNull final Roster genesisRoster);
```

The logic to determine whether to invoke this method could be determined by some mechanism Services deems appropriate.

#### Override Roster

An `Override Roster` is an optional Roster that DevOps may provide for the explicit purpose of starting a network using
a specific state, with a specific set of nodes, such as during a Network Transplant Process. A common example of
this is Mainnet State being transplanted into a testing network.

A quick note on PCES. The software version is used to determine which roster (active or previous) should be used to
validate the signatures of events in the PCES. If the PCES event version is less than the current software version,
it indicates that the event was signed with the old roster, and hence, the old roster is used for validation. If the
PCES event version matches the current software version, the active roster is used. This behavior will be
maintained until Birth Rounds or Dynamic Address Book is implemented.

Therefore, Provided an existing State, an `Override Roster` and a network upgrade (which could be a simple
config-only upgrade if a software version upgrade isn't required), the platform will adopt the provided Override Roster.

The next round number for this network will continue from the last round number in the provided state.
The trigger for adopting the `Override Roster` will be the presence of an Override Roster at start-up with a State
existing on disk. Bear in mind the Network Transplant process is designed primarily with test networks in mind.
Upon adoption of the Override Roster, the Override Roster should be deleted from disk. In test networks where this
process is designed to be used, the Override Roster can be easily accessed and deleted by DevOps.

A new method will be added to the `PlatformBuilder` that will be used by the App to set the Override Roster. In most
cases, as it's the case for a Genesis Roster, there will be no need to call the method on `PlatformBuilder` to set
the Override Roster except in the events we need to transplant a network.

```java
//in PlatformBuilder
/*
  Set the Override Roster for the network.
  <p>
  This method is used to set the optional Override Roster for the network.
  The Override Roster is a special roster used for the sole purpose of Transplanting an existing network.
  The Override Roster, if set, is immutable and will be used to trigger a Network Transplant process.
  <p>
  @param overrideRoster the Override Roster for the network.
 */
PlatformBuilder withOverrideRoster(@NonNull final Roster overrideRoster);
```

#### New Transplant Procedure

The App will decide a network transplant sequence based on the following heuristics:

Network Transplant process == Override Roster AND State provided AND Upgrade.

When both an Override Roster and State are provided, as well as a network upgrade flag set, it signifies a network
transplant mode. The node will discard any existing candidate roster present in the state, rotate the active roster
to the previous roster, and adopt the provided Override Roster as the new Active Roster. This way, the provided
network state and Override Roster are adopted. There will be new code required to create the Override Roster and
pass it to the platform via the `PlatformBuilder` if the network is intended to be in a Transplant mode.
The Services team will be responsible for implementing this, although the details are yet to be defined.

#### Genesis Network process

Genesis Network process == Genesis Roster, No State provided.
When a Genesis Roster is provided, but no pre-existing state exists, it will indicate the creation of a new network.
The node will initiate the TSS key generation process (out of scope for this proposal), create a genesis state with
the provided Genesis Roster, and start participating in consensus from round 1.

#### Keeping existing Network Settings

Keep Network Settings (Normal restart) == No Override Roster AND State provided AND not in Software Upgrade mode. If
only a state is provided without an Override Roster, and the network is NOT in a software upgrade mode, the node
will retain its existing network settings, using the active roster present in the state. This will be the typical
behavior for a node restarting within an established network.

#### Software Upgrade and adoption of Candidate Roster

Software Upgrade Process == No Override Roster AND State provided AND in Software Upgrade mode. When no Override
Roster is provided, but a State is present, and the network IS in a software upgrade mode, the node will start in a
Software Upgrade mode, adopting the Candidate Roster present in the state. If there is no Candidate Roster set in
the state, the Active Roster continues to be used.

### Services Changes, in summary

1. The App will be responsible for creating the Candidate Roster from the Address Book and calling the specified API to
   set it.
2. The App will be responsible for determining the mode it wants the network to start in i.e. one of restart, upgrade,
   genesis, or transplant modes.
3. The App will be responsible for creating the Genesis Roster, when needed, and conditionally passing it to the
   platform.
4. The App will be responsible for creating the Override Roster, when needed, and conditionally passing it to the
   platform.

### DevOps Changes, in summary

1. DevOps will decide whether to keep the `config.txt` model and if so, work with Services on its translation
   into a Roster.
2. DevOps will be responsible for managing the lifecycle of the files on disk that are used in creating the Roster,
   including the new TSS encryption key.
3. DevOps will be responsible for providing the Genesis Roster or Override Roster as files in a location App desires.
4. DevOps will be responsible for cleaning up the Override Roster file after the network transplant process is complete.

## Core Behaviors, in summary

- Roster Creation: Hedera App will create a Candidate Roster from the Address Book.
- Roster Submission: App will trigger roster submission by setting the `Roster` object in the
  `PlatformState` API.
- Roster Validation: The submitted roster is validated and hashed, if valid, and the hash is not already in the state.
- Roster Storage: The Roster is stored in the State as a States API map of Roster Hash to Roster.
  A reference to the candidate roster's hash is also stored in the `RosterState` singleton object.
- Roster Adoption: The candidate roster is always adopted on the next software upgrade.
- Roster Replacement: If a new candidate roster is submitted before the previous one is adopted, the corresponding new
  Candidate Roster will replace the previous.

## Technical Debt Dependencies on Services, DevOps, and Release Engineering

There are a few existing technical debts that the team has agreed to tackle as dependencies for this proposal.

1. Source of Node identity: Nodes infer their node id from the internal hostname matching the address entry in the
   address book. Two nodes on different corporate networks may have the same internal ip address, ex: 10.10.10.1. While
   highly improbable, we’re lucky there hasn’t been a collision so far. The result would be that the software would try
   to start both nodes on the same machine. The resolution is that nodes should get their identity (Node Id)
   specified explicitly through the node.properties file. Services and DevOps will implement this.

2. Off-by-1 problem: Node IDs are 1 less than the node name. For example, the name of the node with node id 0
   is `node1`. This is confusing. The node name is used as the alias in the cryptography and used to name the pem files
   on disk. Node id 0 gets its cryptography through “node1” alias. The resolution is to get rid of node names and
   use Node IDs only. Services and DevOps will implement this.

3. Inversion of Control: The `config.txt` file does not have all the information that the Hedera Address Book needs (
   proxy endpoints, and in the future, Block Nodes). It has verbose information for the platform. Its format could be
   better, and it stores the account number in the memo field. Upon the creation and adoption of rosters in the
   state, `config.txt` is no longer useful. The resolution is to offload this duty off the platform and allow
   Services to use whatever file format that suits them as described earlier. Services will implement this.

### Roster changes needed for Components

#### Reconnect

Reconnect logic currently exchanges and validates the address book between the learner and teacher nodes. The
learner node uses the address book to select the teacher node, as well as remove all invalid signatures from the
state. Both of these will need to be updated to use rosters instead.

#### Others

- Networking. Most parts of the network code have been abstracted away from the address book as part of the Dynamic
  Address Book effort, so there should be minimal work left there.
- Event Validation. The event validation logic uses the address book to determine whether a given event has a valid
  signature. This will be updated to use rosters instead.
- Miscellaneous components. Other components that reference things stored in the address book (like consensus needing
  the node weight or the crypto module verifying keys) will need to be updated to use things stored in rosters instead.
  Some of these components include SelfEventSigner, EventHasher, HealthMonitor, SignedStateSentine, PcesSequencer,
  StateHasher, HashLogger, etc.

## Test Plan

Some of the obvious test cases to be covered in the plan include validating one or more of the following scenarios:

1. New valid Candidate Roster created with no subsequent one sent by App. Verify accept.
2. New valid Candidate Roster created with a subsequent one sent by App. Verify accept.
3. Invalid roster(s) sent by the App. Verify reject.
4. Node Failures During Roster Change: What happens if nodes fail or disconnect during a roster change? Verify valid
   node successfully reconnects.
5. Concurrent Roster Updates: What if we make multiple roster updates concurrently? Verify no effect on adoption.
6. Roster recovery? The Node receives a candidate roster, crashes. Wake up, reconnect. Verify recovery.
7. What end-to-end testing do we need for a brand-new network that uses the TSS signature scheme to sign its blocks?
8. A node goes offline and skips a software version upgrade but remains in the active roster used by the network. Comes
   back online. Verify it can still successfully rejoin the network.
9. A node goes offline and skips a bunch of software version upgrades but remains in the active roster.
   Comes back online, but The old version of the roster that was preserved when it died still contains at least one node
   that is still active on the network when the node comes back online. Verify it can still successfully rejoin the
   network.

## Metrics

We propose that some metrics be added. One useful metric we will introduce is the number of candidate rosters that have
been set. Others may be introduced during implementation.

## Implementation and Delivery Plan

- Define Roster Data Structure in protobuf: Design and implement the Roster class and the associated child classes.
- Develop the API: Method added and implemented in `PlatformState` to set a candidate roster.
- Modify State Storage: Implement specified data structures and candidate roster logic.
- Update Components: Modify all platform components that currently use `AddressBook` to use `Rosters` instead, if any.
- Testing: Conduct thorough testing as specified in this proposal.
- Deployment: Work with DevOps and Release Engineering to deploy the changes.
