# TSS - Roster Proposal

---

## Summary

This proposal outlines the design specification of rosters to support Threshold Signature Scheme (TSS) block signing.
It details the roster's lifecycle, data storage, API, DevOps workflow, and necessary changes to associated
components within the Hedera platform.

|      Metadata      |                   Entities                    |
|--------------------|-----------------------------------------------|
| Designers          | Kore, Anthony, Ed, Austin                     |
| Functional Impacts | TSS, Address Book, Reconnect,                 |
| Related Proposals  | TSS-Library, TSS-Ledger-Id, TSS-Block-Signing |
| HIPS               | N/A                                           |

## Purpose and Context

The introduction of the Threshold Signature Scheme (TSS) requires a new mechanism for managing node participation in
consensus and block signing. The Roster, a subset of the address book, will provide the data structure and API for this mechanism.
This proposal provides a specification for the behavior of rosters from creation to terminal state.
A roster reaches a terminal state when it is removed from the state store.
The details of how the roster is created, validated, stored, adopted and removed are outlined in this proposal.

## What this proposal does not address

1. Details relating to what components will call the new APIs to store and retrieve rosters. This belongs in the other TSS proposals.
2. How devops will manage the lifecycles of artifacts used in creating the roster.

## Requirements

1. Immutability: The roster hash will be a critical piece of data. Rosters must be immutable to protect the integrity of its computed hash.
2. Efficient Storage: Roster data should be stored efficiently in the state to minimize overhead and unnecessary data copying.
3. Clear API: A well-defined API should be provided to create and submit candidate rosters, and for components to manage the lifecycle of submitted rosters.
4. Components update: Components that currently rely on the use of the Address book (such as Reconnect, Network, Event Validation, etc.) must be adapted to use the new `Roster` object instead.
5. Address book paradigm discontinued within the current platform side of the codebase in favor of `Roster`s.
6. There must be a well-laid-out mechanism for the start-up code to arrive at the correct roster to use in starting the network.

## Architecture

### Roster Types.

This proposal introduces four types of `Roster`s - candidate `Roster`, active `Roster`, genesis `Roster`, and override `Roster`.

1. A Candidate `Roster` is a Roster that is currently being considered for adoption to become the active `Roster`.
2. An Active `Roster` is the roster currently being used to determine consensus.
3. An Override `Roster` is a special Roster used to override an existing active `Roster` during a network transplant.
4. A Genesis `Roster` is a special roster used for bootstrapping a brand-new Genesis network.

### Roster Public API

#### Setting the Candidate Roster

At some point when the Hedera App (henceforth referred to as 'App') decides it's time to begin work on adopting a new address book (scope beyond this proposal),
it will create a candidate `Roster` object with information from the Address Book and set it to be stored it in the state.
A candidate roster already stored in the state can also be replaced by a new one required by the App.

To manage `Roster` storage and retrieval from the state, a new set of state `store` APIs will be introduced, similar to existing store implementations.
Stores are an abstraction over state storage and retrieval. They provide a way for components to interact with states without having to specify implementation details.
Store APIs are typically Readable or Writable. This proposal introduces a `ReadableRosterStore` and a `WritableRosterStore`.

The `ReadableRosterStore` will have access methods such as `getCandidateRoster()`, `getActiveRoster()` and `getRosterHistory()`.
These will respectively return the candidate `Roster`, active `Roster`, and the history of active rosters present in the state.

A `WritableRosterStore` implementation will have methods to store `roster`s in the state such as `setCandidateRoster()` and `setActiveRoster()`.
The `WritableRosterStore#setCandidateRoster()` method will be used to set a candidate `Roster` in the state as follows:

1. Validate the candidate `Roster`.
2. Store the candidate `Roster` hash in the `RosterState`
3. Store the candidate Roster itself in the `RosterMap`.

The `WritableRosterStore#setActiveRoster()` method will be called to set a new active `Roster` in the state. A call to `setActiveRoster` will infer the following operations:

1. Invalidate and remove any existing candidate `Roster` present in the state
2. Rotate the current active `Roster` to the previous active `Roster`
3. Set the new Roster as the active `Roster` in the state

```java
//in WritableRosterStore

void setCandidateRoster(@NonNull final Roster candidateRoster);
void setActiveRoster(@NonNull final Roster roster, final long round);
```

```java
//in ReadableRosterStore

@Nullable
Roster getCandidateRoster();
Roster getActiveRoster();
```

The steps to validate and add the Roster to the state will be executed within the same thread that submits the request.
A checked exception will be thrown if the Roster is invalid. The durability of the submitted Roster is ensured by storing it in the state.

## Data Structure

The App is already responsible for managing the address book.
We propose that it continues to do so, and when it deems necessary (such as during a freeze upgrade), creates a candidate `Roster` object and stores it via the new API.

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
 * The current state of rosters.<br/>
 * This message stores a roster data in the network state.
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

It is noteworthy that the roster must be immutable to guarantee the integrity of the computed hash.
This map of rosters will typically contain the current active `Roster`, an optional previously active `Roster`, and an optional current candidate `Roster`.
Insertion of rosters will be controlled by ensuring that the acceptance of a new Candidate Roster invalidates and removes the current candidate `Roster`.
Therefore, the map is expected to have a maximum size of three elements.

The new `RosterState` will store the `candidateRosterHash` and a list `roundRosterPairs` of pairs of round numbers and the active `Roster` that was used for that round.
The `roundRosterPairs` list will be updated only when the active `Roster` changes.
In the vast majority of cases, the list will contain only one element, the current active `Roster`.

Immediately following the adoption of a candidate `Roster`, this list will have two elements — the previous active `Roster` and the current active `Roster` (which was the candidate `Roster`).
The choice of a list is suitable here as it is much cheaper to iterate over 2-3 elements comparing their `long` round numbers than it is to do a hash lookup.
When we reach full Dynamic Address Book, this design choice may need to be revisited.

If a `candidateRosterHash` hash entry already exist (hash collisions) in the map of Rosters, it will be discarded.
That is, setting a candidate `Roster` is an idempotent operation. One benefit of this indirection (using a Map instead of a Singleton) is that it avoids moving data around in the merkle state.
Another benefit is that adoption trigger becomes straightforward (App sets the roster) with delineated responsibilities between components.

### Roster Validity

A Roster is considered valid if it satisfies the following conditions:

1. The roster must have at least one RosterEntry.
2. At least one RosterEntry/ies must have a non-zero weight.
3. All RosterEntry/ies must have a valid gossip_ca_certificate.
4. All RosterEntry/ies must have a valid tss_encryption_key.
5. All RosterEntry/ies must have at least one gossip Endpoint.
6. All ServiceEndpoint/s must have a valid IP address or domain name (mutually exclusive), and port.
7. The roster must have a unique NodeId for each RosterEntry.

On the submission of a new Candidate Roster, it will be validated against these conditions.
Note that a constructed `Roster` can be valid, but not accepted by the API.
For example, if a new candidate roster is set via the API, but its hash evaluates to the hash of the existing candidate roster, the new roster will be discarded. That is, the operation has no effect.

## Data storage

### State

All the data related to Active and Candidate Roster will be stored in the State. Two new objects will be added to
the state:

1. A `RosterState` singleton object that will store the current candidate roster hash and a list of round numbers
   and hashes of the active roster used to sign each round.
2. A map of rosters, keyed by roster’s hash - ```Map<Hash<Roster>, Roster>```. All candidate and active rosters will be
   stored here. When a new candidate roster is received, it will be stored in this map, with the `RosterState`'s current candidate roster hash updated.

### On Disk

There will continue to be components used in creating the Roster - such as the private EC (elliptic curve) key, the RSA
private key and the signing X509Certificate certificate - that will be stored on disk.
This proposal also introduces new artifacts that will be stored on disk such as the `genesis-config.txt` and `override-config.txt` files.
However, it will be the responsibility of the devops team to manage the lifecycle of these files.

## Services and DevOps changes (Inversion of control)

The current startup procedure will be altered as follows:

- The `config.txt` file will no longer be used for storing the address book in existing networks.
- Designating `config.txt` introduces inversion of control. It will be at the exclusive prerogative of Services (the App) going forward.
  It's ownership (current platform components) will no longer be responsible for it, and all the code that builds an AddressBook from `config.txt` will be removed or refactored and moved into the current Services part of the codebase.

The current network Transplant procedure is manual. DevOps is given a State and `config.txt` file on disk.
This is then followed by a config-only upgrade to adopt the `config.txt` file using that state.
This will change to the use of a Genesis Roster or Override Roster.
The new startup sequence will be determined by the presence of a Genesis Roster, State, or Override Roster as described in the following sections.

### The new Startup Behavior

There will be four possible startup modes for the network - Genesis Network, Network Transplant, Normal restart, and Software Upgrade modes.

Starting in a Genesis Network will require a `Genesis Roster`, while a Network Transplant will require an `Override Roster`.
Normal restart and Software Upgrade modes will use the existing roster present in the state or fallback to creating one from `config.txt` file as described in the following diagram.
![](TSS%20Roster%20Lifecycle-Proposed%20Startup%20Behavior.drawio.svg)

The mechanism of arriving at Genesis or Override Roster is as follows.

#### Genesis Roster

A `Genesis Roster` is an optional Roster that DevOps may provide for the explicit purpose of starting a Genesis Network
Process. It is essential to distinguish the Genesis Roster from the Candidate, Active, or Override rosters.
The Genesis Roster is a special roster used for the sole purpose of bootstrapping a brand-new Genesis network; one in which there is no existing State, and the round number begins at 1.
However, its structure is exactly the same as the Active or Candidate Rosters.

A new, special-purpose config.txt file will be introduced and named `genesis-config.txt`.
On startup, the determination logic (of the correct roster to use) will check for the existence of this`genesis-config.txt` file.
This file will be in the same location and format as the current `config.txt` file.

A start-up determination logic will check if there is a `Roster` present in the state.
If not, the determination logic will look for a `genesis-config.txt` on disk.
If found, a Genesis Roster will be created from it and used as the active `Roster` for the network.

#### Override Roster

An `Override Roster` is an optional Roster that DevOps may provide for the explicit purpose of starting a network using a specific state, with a specific set of nodes, such as during a Network Transplant Process.
A common example of this is Mainnet State being transplanted into a testing network.

**A quick note on Pre-Consensus Event Stream (PCES).**

In the current PCES implementation, the software version is used to determine which address book (current or previous)
should be used to validate the signatures of events in the PCES.
If the PCES event version is less than the current software version, it indicates that the event was signed with the old roster, and hence, the old address book is used for validation.
If the PCES event version matches the current software version, the current address book is used.
This proposal maintains this exact behavior (until Birth Rounds or Dynamic Address Book is implemented), but it replaces the use of address books with rosters.
Instead of using current and previous `AddressBook`s, active and previous active `Roster`s will be passed to PCES.
A previous active `Roster`, in most cases, is the Roster that was active before the current Active Roster roster.
There is an edge case in which this definition of Previous Roster will not hold true, and the previous active Roster will be artificially created instead of being the roster that was previously active.
See Edge Case 1 below for more details.

Like the Genesis Roster, the Override Roster will be created from a special-purpose config file named `override-config.txt`.
This file will be in the same location and format as the current `config.txt` file.

Therefore, Provided an existing State, an `override-config.txt` and a network upgrade (which could be a simple config-only upgrade if a software version upgrade isn't required), an `Override Roster` will be constructed from the `override-config.txt` and adopted as the active `Roster`.
Now might be a good time to revisit the startup decision tree diagram for reference.

The next round number for this network will continue from the last round number in the provided state.
Note that the Network Transplant process is designed primarily with test networks in mind.
Upon adoption of the `Override Roster`, the `override-config.txt` will be renamed on disk by the implementation code that just adopted it.
In test networks where this process is designed to be used, the renamed `override-config.txt` file will be easily accessible and removable by DevOps.

**More on Genesis and Transplant Modes**

Genesis Network process == Genesis Roster (i.e. valid `genesis-config.txt` file exists), No State provided.
When a Genesis Roster is provided, but no pre-existing state exists, it will indicate the creation of a new network.
The node will initiate the TSS key generation process (out of scope for this proposal), create a genesis state with the provided Genesis Roster, and start participating in consensus from round 1.

Network Transplant process == Override Roster (i.e. valid `override-config.txt` file exists) AND State provided AND Upgrade.
When both an `override-config.txt` file and a State are provided, as well as a network upgrade flag set, it signifies a network transplant mode.
The node will discard any existing candidate `Roster` present in the state, rotate the active `Roster`to the previous roster, construct an `Override Roster` from the provided `override-config.txt` file, and adopt the provided Override Roster as the new active `Roster`.
This way, the provided network state and Override Roster are adopted.

#### Normal Restart Mode (Keeping existing Network Settings)

Normal restart (Keep Network Settings) == No Override Roster AND State provided AND not in Software Upgrade mode.
If only a state is provided without an `override-config.txt` file and the network is NOT in a software upgrade mode, the network will retain its existing settings and re-use the active `Roster` present in the state.
This will be the typical behavior for a node restarting within an established network.

#### Software Upgrade Mode (adoption of Candidate Roster)

Software Upgrade Process == No Override Roster AND State provided AND in Software Upgrade mode.
When no `override-config.txt` file is provided, but a State is present, and the network IS in a software upgrade mode, the node will start in a Software Upgrade mode, adopting the candidate `Roster` present in the state.
If there is no candidate `Roster` set in the state, the active `Roster` continues to be used.
If there's no active `Roster` found in the state, one will be loaded from the `config.txt` file.

### A new Determination Logic for active `Roster` (and History)

![StartupAndRosters-TSS-Roster.drawio.svg](StartupAndRosters-TSS-Roster.drawio.svg)

A new logic will be introduced and called from `ServicesMain` class to determine the mode the network should start in and the roster to use.
The logic will derive all the possible roster types (from state or on disk) and determine the final active `Roster` to be used based on the decision tree.
This final active `Roster` will be set in the state and used for the network startup.

In addition, the previous active `Roster` will also be determined.
A previous active `Roster` is the active `Roster`that was last used before the current one.

There will be two ways to determine the previous active `Roster`:
by simply getting one from the state using `ReadableRosterStore#getRosterHistory()`)
or by (rarely) constructing one from `config.txt` (fallback mechanism).

Once the active `Roster` History has been determined, it will be passed to the `PlatformBuilder`.

#### Edge Case1: Constructing a Previous Active Roster from `config.txt`

The case in which a previous active `Roster` is constructed from `config.txt` is an edge case.
The first time the Network adopts the use of Rosters, there will be no previous active `Roster` in the state, and the active `Roster` will be constructed from legacy `config.txt` file.
The previous active `Roster` will however be a Roster constructed from a `currentAddressBook` that will be loaded from the `PlatformState`.

Upon determining the active `Roster` and Previous active `Roster`, the previous active `Roster` will be set in the state first as an active `Roster` via the `setActiveRoster` call followed by a later call to the same method with the active `Roster`.
Recall the `setActiveRoster` call requires a round number.
The round number that will be used for the call with the Previous active `Roster` will be 0.
This ensures there is a Roster in the history for all possible PCES events.
The round number that will be used for the call with active `Roster` is the round number of the last event in the state + 1.

Note that this solution works until we get to full Dynamism. At that point, this mechanism will be revisited.
Until then, the round numbers used here are enough for the PCES to function correctly.

Setting both rosters in the state builds the roster history in the state, which makes it available for retrieval via the`getRosterHistory` call described earlier.

#### Edge Case2: Previous Active Roster has a hash collision with Active Roster

In the rare case the active `Roster` is the same as the Previous active `Roster`, a call to `setActiveRoster` will result in the Roster not getting stored as it will be found to already exist.
This is expected to happen the very first time the network adopts the use of Rosters, where the active `Roster`constructed from `config.txt` and the Previous active `Roster` constructed from the `currentAddressBook` in the `PlatformState` might be the same.
In this case, as the Roster History will contain only one element, the active `Roster` will be duplicated in the Roster History instead.
The Round number assignments used in Edge Case 1 above will still apply.

### Feature Flag for Roster Adoption

A new feature flag will be introduced to signify whether to use the determination logic described in the previous section or continue to use legacy `config.txt` to create/determine Address Books.
The feature flag will be set in the configuration file and retrievable via some call similar to `config.useRosterConfigTxt()`.

When this feature flag is turned off, the network will load AddressBook from `config.txt` and create a Roster from this AddressBook as it is done today.
The current and previous active address books in the `PlatformState` will continue to be used.

Regardless of the status of this flag, on the current Platform side of the codebase, the use of `AddressBook`s will be replaced by `Roster`s.
However, the mechanism through which the Roster is created will differ based on the status of the feature flag.

### DevOps Changes, in summary

1. DevOps will decide whether to keep the `config.txt` model and if so, work with Services on its translation into a Roster.
2. DevOps will be responsible for providing the `config.txt`, `genesis-config.txt` or `override-config.txt` as files in the desired location.
3. DevOps will be responsible for managing the lifecycle of the files on disk that are used in creating the Roster, including the new TSS encryption key.
4. DevOps will be responsible for cleaning up the `override-config.txt` file after the network transplant process is complete.

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
   proxy endpoints, and in the future, Block Nodes). Its format could be better, and it stores the account number in the memo field.
   Upon the creation and adoption of rosters in the state, `config.txt` is no longer useful.
   The resolution is to offload this duty off the current platform components and allow Services components to use whatever file format that suits them as described earlier.
   Services will implement this.

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
- Update Components: Modify all current platform components that currently use `AddressBook` to use `Rosters` instead, if any.
- Testing: Conduct thorough testing as specified in this proposal.
- Deployment: Work with DevOps and Release Engineering to deploy the changes.
