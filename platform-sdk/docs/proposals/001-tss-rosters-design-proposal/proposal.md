# TSS - Roster Proposal

---

## Summary

This proposal outlines the design specification of rosters to replace the use of the platform's `AddressBook` data
structure and replace the use of config.txt as the medium of performing `AddressBook` updates. This proposal
details the roster's lifecycle, data storage, API, and necessary changes to associated components within the
platform. The implementation of this proposal is a necessary pre-requisite for the implementation of the Threshold
Signature Scheme (TSS).

|      Metadata      |                   Entities                    |
|--------------------|-----------------------------------------------|
| Designers          | Kore, Anthony, Ed, Austin                     |
| Functional Impacts | TSS, Address Book, Reconnect,                 |
| Related Proposals  | TSS-Library, TSS-Ledger-Id, TSS-Block-Signing |
| HIPS               | N/A                                           |

## Purpose and Context

The term `AddressBook` has been ambiguously used to refer to both the Hedera Address Book and the Platform Address
Book. The Platform Address Book is a proper subset of the Hedera Address Book that is only concerned with the
consensus nodes, their consensus weight, gossip endpoints, and related cryptography. The Hedera Address Book
contains additional information that is not relevant to the platform portion of the consensus nodes, such as the
account numbers of the nodes, their staked hbar, the proxy endpoints, and the existence of other types of nodes in
the network. We've decided to disambiguate the two by referring to the Platform Address Book as the `Roster` and
making their lifecycle distinct from each other. The life-cycle for updating the Roster must also change as a
pre-requisite to the implementation of Threshold Signature Scheme (TSS) and the generation of network Ledger Ids.

Prior to this proposal, after the completion of the Dynamic Address Book (DAB) Phase 2 effort, the platform
`AddressBook` is updated by writing out a new config.txt file and updated cryptography to disk in response to a
freeze upgrade transaction, just before shut down. Out of band, the new config.txt and cryptography are moved to
replace the previous ones on disk. On software startup, the new config.txt and cryptography are read in and adopted
during a software upgrade. Discrepancy in the information present between the `Roster` data structure and the
`AddressBook` data structure make config.txt a poor medium for carrying out `Roster` updates. Writing the updated
roster out to disk is less secure than storing it in the state. When TSS is implemented, there will be TSS related
key material stored in the state, and it is imperative that this key material stay in sync with the `Roster` data.

After this proposal is implemented, new rosters will be created and stored in the state for adoption at a software
upgrade boundary. It has been common practice in testing to use manually created config.txt files to start new
networks from genesis and transplant merkle state to a different new network from an existing network. To continue to
allow these processes, alternate mechanisms of providing a genesis roster and a network overriding roster will be
introduced.

### Dependencies, Interactions, and Implications

The implementation of the TSS-Roster proposal must come after the completion of DAB Phase 2 and must be completed
before TSS-Ledger-ID.

This change in the mechanics of how the roster is updated, new networks are started, and how merkle state is
transplanted will impact current DevOps practices significantly. SOLO and all testing environments will need to be
updated as well. This change includes the inversion of control of the startup process where in the responsibility
for reading the state, address book, and configuration from disk is moved from the platform to `ServicesMain`.

This roster life-cycle change has been carved off from the TSS effort as a separate proposal to reduce the size and
scope of changes per release and allow the changes entailed by TSS to be more incremental.

### Requirements

1. Immutability: Rosters must be immutable to protect the integrity of its computed hash.
2. Efficient Storage: Roster data stored in the state should minimize overhead and unnecessary data copying.
3. Clear API: To create and submit candidate rosters, and to manage the lifecycle of submitted rosters.
4. Components update: Components that currently rely on the use of the Address book (such as Reconnect, Network, Event
   Validation, etc.) must be adapted to use the new `Roster` object instead.
5. Address book paradigm discontinued within the current platform side of the codebase in favor of `Roster`s.
6. There must be a well-laid-out mechanism for the start-up code to arrive at the correct roster to use in starting the
   network.

### Design Decisions

The roster data has been reduced to the bare essentials required by the platform.

1. A `Roster` is a list of `RosterEntry` in order of NodeId.
2. A `RosterEntry` contains the following fields for a consensus node in the network:
   1. nodeId
   2. consensus weight
   3. gossip_ca_certificate - containing the gossip signing public key
   4. list of gossip network endpoints

The following `Address` data has been dropped:

1. `nickname` - not relevant to the platform
2. `selfname` - used as the alias for cryptography and is now based on the node id
3. `encryption public key` - never used
4. `agreement public key` - now created on startup, used in networking, and does not belong as part of the roster
5. `memo` - used to store the account number in the Hedera Address Book and is not relevant to the platform
6. `internal ip address and port` - This will eventually be replaced with node specific configuration.

In order to make a smooth transition from the use of config.txt to using a roster stored in the state, the fields
related to `internal` ip address and port are being stored in the list of gossip network endpoints in the roster. A
platform design proposal in the near future will move the `internal` ip address and port field to node configuration.
In the end, there will only be external endpoints in the roster. In this proposal, we only connect to a node's
external gossip endpoint. The internal endpoint is ignored.

The use of config.txt for starting new networks from genesis and transplanting merkle state to a new network will
be replaced and require the presence of specific files on disk to initiate these scenarios. A `genesis-config.txt`
file will be used to start a new network from genesis, and an `override-config.txt` file will be used to transplant
merkle state to a new network. Initiating these scenarios through distinct files will make the logic for
determining which roster to use at startup more straightforward.

To be consistent with future consensus design, the running platform code will not directly read from or write to the
merkle state. The appropriate roster data will be provided to the platform during platform construction.

#### Alternatives Considered

Continuing to use config.txt as the medium for updating the platform `AddressBook` was considered. However, the
discrepancy in the information present between the `Roster` data structure and the `AddressBook` data structure make
config.txt a poor medium for carrying out `Roster` updates. Writing the updated roster out to disk is less secure
than storing it in the state. When TSS is implemented, there will be TSS related key material stored in the state,
and it is imperative that this key material stay in sync with the `Roster` data. Leaving the content as a modifiable
file on disk would pose risks of introducing errors.

## Changes

The platform `AddressBook` lifecycle is being replaced with a new `roster` lifecycle. A new feature flag
`addressbook.useRosterLifecycle` will be used to toggle between the current `config.txt` based address book
lifecycle and the new `roster` lifecycle. The feature flag will be set to `false` by default. The new roster
lifecycle will be enabled by setting the flag to `true`.

### Architecture and/or Components

No new components are proposed. Without loss of capability, the platform `AddressBook` data structure is replaced in
all platform components with the `Roster` data structure, including in the `ConsensusRound` data structure that is
passed to the application. If the application is dependent on any of the removed fields on an address in the
`AddressBook` data structure, it will have to look that information up from its own data storage for the Hedera Address
Book.

Updating the roster is now performed through writing a `candidate` roster in the state instead of writing a new
`config.txt` and the associated gossip certificates to disk.

#### Roster Types.

This proposal introduces four types of `Roster` - `active`, `candidate`, `genesis`, and `override`.

1. `Active Rosters` are paired with the round numbers for when they became active and are used to validate event
   signatures. The active roster with latest round number is used to determine consensus
   * The `Roster History` is a list of active rosters paired with the rounds they became active. At this time the
     history length is at most 2.
     * `currentRoster` - the active roster in the history paired with the highest round number.
     * `previousRoster` - the active roster in the history paired with the lowest round number.
     * In this document we denote the roster history as an ordered list of pairs by descending round
       number. Example:  [(currentRoster, 2), (previousRoster, 1)].
2. A `Genesis Roster` is loaded from disk at the genesis of a new network and becomes the first active roster.
3. The `Candidate Roster` is a roster that will become active at the next software upgrade.
4. An `Override Roster` is loaded from disk and unconditionally set as the latest active roster. This is used to
   transplant the state to a different network.

### Core Behaviors: Roster Lifecycle

The following diagram depicts the new roster life-cycle where the `Roster History` provided to the platform is
determined at startup and committed to the merkle state before handing it to the platform.

On software upgrade, during normal execution, if a candidate roster is present in the state, it will be unconditionally
adopted as the current or latest active roster in the new roster history. The round number for the new active
roster will be the round number of the state + 1. The adoption of a new active roster updates the old roster history
by causing the old current roster to become the new previous roster. The old previous roster is removed from the
history and the merkle state. After the new roster history is determined, it is handed to the platform during
construction.

The `Roster History` is needed by the platform at startup for event validation during Pre-Consensus Event Stream (PCES)
replay. In the future, the birth round of the event will be used to determine which roster to use for validation. In
the current implementation, the platform expects two `AddressBooks`, the `current` and `previous` address books.
The `previous` address book is used to validate events that have older software versions than the current software
version. When we switch to rosters, the previous and current roster will be used in place of the previous and
current address book. The previous roster will continue to be used to validate events that have an older software
version. The switch to using event birth rounds will happen in a future proposal for Dynamic Address Book phase 3.

During the handling of a freeze upgrade transaction, instead of writing the new config.txt to disk, a new candidate
roster is created and stored in the roster merkle state.

NOTE: The roster state maintains its own `Roster History`. Setting the active roster in the roster state will cause
the history to be updated by rotating the current active roster to the previous active roster and setting the new
roster as the current active roster. The old previous active roster is removed from the history and the merkle state.

![StartupAndRosters-TSS-Roster.drawio.svg](StartupAndRosters-TSS-Roster.drawio.svg)

#### Startup: Determining The New Roster History

There are four distinct modes that a node can start in:

* `Genesis Network` - The node is started with a genesis roster and no pre-existing state on disk.
* `Network Transplant` - The node is started with a state on disk and an overriding roster for a different network.
* `Software Upgrade` - The node is restarted with the same state on disk and a software upgrade is happening.
* `Normal Restart` - The node is restarted with the same state on disk and no software upgrade is happening.

A decision tree for deciding which mode to start the network in is as follows:

![](TSS%20Roster%20Lifecycle-Proposed%20Startup%20Behavior.drawio.svg)

##### MODE: New Network Genesis

Conditions:

1. No state loaded from disk
2. A `genesis-config.txt` file is present on disk

Ignored Files: (Log warning if present)

1. `config.txt`
2. `override-config.txt`

Startup Tasks:

1. Translate `genesis-config.txt` to a genesisRoster.
2. Create a genesis state with the genesisRoster.
3. Set (genesisRoster, 0) ase the new active roster in the roster state.
4. rosterHistory := `[(genesisRoster, 0)]`
5. Start the platform with the rosterHistory.

Cleanup Tasks:

1. Once the first saved state is created, All `config.txt` related files are moved to subdirectory `.
   archive/yyyy-MM-dd_HH-mm-ss/`.

##### MODE: Software Upgrade

Conditions:

1. A state is loaded from disk
2. A software upgrade is happening
3. No `override-config.txt` file is present on disk

Ignored Files: (Log warning if present)

1. `genesis-config.txt`
2. `config.txt` - if a candidate roster is present in the state

Startup Tasks:

1. If there is a candidate roster in the state (Non-Migration Software Upgrade)
   1. candidateRoster := read the candidate roster from the roster state.
   2. currentRound := state round +1
   3. (previousRoster, previousRound) := read the latest (current) active roster and round from the roster state.
   4. new rosterHistory := `[(candidateRoster, currentRound), (previousRoster, previousRound)]`
   5. clear the candidate roster from the roster state.
   6. set (candidateRoster, currentRound) as the new active roster in the roster state.
   7. Start the platform with the new rosterHistory.
2. If the roster state is empty: no candidate roster and no active rosters. (Migration Software Upgrade)
   1. Read the current AddressBooks from the platform state.
   2. configAddressBook := Read the address book in config.txt
   3. previousRoster := translateToRoster(currentAddressBook)
   4. currentRoster := translateToRoster(configAddressBook)
   5. currentRound := state round +1
   6. rosterHistory := `[(currentRoster, currentRound), (previousRoster, 0)]`
   7. set (previousRoster, 0) as the active roster in the roster state.
   8. set (currentRoster, currentRound) as the active roster in the roster state.
   9. Start the platform with the new rosterHistory.

Cleanup Tasks:

1. Once the first saved state is created, All `config.txt` related files are moved to subdirectory `.
   archive/yyyy-MM-dd_HH-mm-ss/`.

##### MODE: Normal Restart

Conditions:

1. A state is loaded from disk
2. No software upgrade is happening

Ignored Files: (Log warning if present)

1. `config.txt`
2. `genesis-config.txt`
3. `override-config.txt`

Startup Tasks:

1. If there exists active rosters in the roster state.
   1. Read the active rosters and construct the existing rosterHistory from roster state
   2. Start the platform with the existing rosterHistory.
2. If there is no roster state content, this is a fatal error: The migration did not happen on software upgrade.

##### MODE: Network Transplant

Conditions:

1. A state is loaded from disk
2. A software upgrade is happening
3. An `override-config.txt` file is present on disk

Ignored Files: (Log warning if present)

1. `genesis-config.txt`

Startup Tasks:

1. If there is a candidate roster in the state (Non-Migration Software Upgrade)
   1. candidateRoster := read the candidate roster from the roster state.
   2. overrideAddressBook := read the address book from override-config.txt
   3. overrideRoster := translateToRoster(overrideRoster).
   4. currentRound := state round +1
   5. (previousRoster, previousRound) := read the latest (current) active roster and round from the roster state.
   6. new rosterHistory := `[(overrideRoster, currentRound), (previousRoster, previousRound)]`
   7. clear the candidate roster from the roster state.
   8. set (overrideRoster, currentRound) as the new active roster in the roster state.
   9. Start the platform with the new rosterHistory.
2. If the roster state is empty: no candidate roster and no active rosters. (Migration Software Upgrade)
   1. Read the current and previous AddressBooks from the platform state.
   2. overrideAddressBook := Read the address book from override-config.txt
   3. overrideRoster := translateToRoster(overrideAddressBook)
   4. previousRoster := translateToRoster(currentAddressBook)
   5. currentRound := state round +1
   6. rosterHistory := `[(overrideRoster, currentRound), (previousRoster, 0)]`
   7. set (previousRoster, 0) as the active roster in the roster state.
   8. set (overrideRoster, currentRound) as the active roster in the roster state.
   9. Start the platform with the new rosterHistory.

Cleanup Tasks:

1. Once the first saved state is created, All `config.txt` related files are moved to subdirectory `.
   archive/yyyy-MM-dd_HH-mm-ss/`.

#### Shutdown: Setting The Candidate Roster From The Freeze Upgrade Transaction

The [current implementation](https://github.com/hashgraph/hedera-services/blob/9cc0ab85e50337000a406aeb51d00dfb523f8034/hedera-node/hedera-network-admin-service-impl/src/main/java/com/hedera/node/app/service/networkadmin/impl/handlers/ReadableFreezeUpgradeActions.java#L275)
writes the new config.txt and cryptography to disk during a freeze upgrade transaction. At this code location, the
`addressBook.useRosterLifecycle` flag will be used to toggle between the old method of writing config.txt and gossip
certificate PEM files to disk, and the new method to set the candidate roster in the state to be adopted at the
software upgrade taking place in the next startup.

#### Roster State

The roster state is organized into 3 parts:

1. A map from roster hashes to rosters.
2. A list of pairs of round numbers and roster hashes. (Roster History)
3. The hash of the candidate roster.

Invariants and Allowances:

1. The length of the roster history is at most 2.
2. The roster map can have at most 3 rosters in it.
3. The roster map keys must be exactly the roster hashes for the candidate, current, and previous rosters.
4. The current, previous, and candidate rosters are allowed to be the same roster hash.
   * This is expected to be exceedingly rare.
5. The roster history is updated purely through setting a new active roster whose round number is strictly higher
   than all round numbers in the history.
   * If setting a new active roster would increase the history length to 3, the roster with the lowest round number
     is removed.
   * Removing a roster from the history also removes it from the roster map **if no other remaining roster has the same
     hash**.
6. The roster hash is calculated through our normal protobuf hashing mechanism.
7. All candidate and active rosters set must be valid.

###### Roster Validity

A Roster is considered valid if it satisfies the following conditions:

1. The roster must have at least one RosterEntry.
2. At least one RosterEntry/ies must have a non-zero weight.
3. All RosterEntry/ies must have a valid gossip_ca_certificate.
4. All RosterEntry/ies must have at least one gossip Endpoint.
5. All ServiceEndpoint/s must have a valid IP address or domain name (mutually exclusive), and port.
6. The roster must have a unique NodeId for each RosterEntry.
7. RosterEntries must be sorted in the ascending order of their node ids.

### Roster Public API

Following the pattern for other services that read and write to the merkle state, the roster data structure will be
protobuf and the roster state will have readable and writable roster stores that manage the invariants on the data
in the merkle state.

#### Roster Protobuf

A `Roster` is a list of `RosterEntry`.

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

/**
 * A single roster entry in the network state.
 *
 * All fields are REQUIRED.
 */
message RosterEntry {

    // The tssEncryptionKey is no longer in the roster.
    reserved 4;
    reserved "tssEncryptionKey";

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

    reserved 4;
    reserved "tssEncryptionKey";
}
```

#### Roster State Protobuf

The `RosterState` is a candidate roster and a list of round-roster(hash) pairs.

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

#### Roster Store APIs

##### ReadableRosterStore

```java
/**
 * Read-only implementation for accessing rosters states.
 */
public interface ReadableRosterStore {
    /**
     * Gets the candidate roster if found in state or null otherwise.
     * Note that state commits are buffered,
     * so it is possible that a recently stored candidate roster is still in the batched changes and not yet committed.
     * Therefore, callers of this API must bear in mind that an immediate call after storing a candidate roster may return null.
     *
     * @return the candidate roster
     */
    @Nullable
    Roster getCandidateRoster();

    /**
     * Gets the active roster.
     * Returns the active roster iff:
     *      the roster state singleton is not null
     *      the list of round roster pairs is not empty
     *      the first round roster pair exists
     *      the active roster hash is present in the roster map
     * otherwise returns null.
     * @return the active roster
     */
    @Nullable
    Roster getActiveRoster();

    /**
     * Gets the roster history.
     * Returns the active roster history iff:
     *      the roster state singleton is not null
     *      the list of round roster pairs is not empty
     *      the active roster hashes are present in the roster map
     * otherwise returns null.
     * @return the active rosters
     */
    @Nullable
    List<RoundRosterPair> getRosterHistory();

    /**
     * Get the roster based on roster hash
     *
     * @param rosterHash The roster hash
     * @return The roster.
     */
    @Nullable
    Roster get(@NonNull Bytes rosterHash);

}
```

##### WritableRosterStore

```java
/**
 * Read-write implementation for accessing rosters states.
 */
public class WritableRosterStore extends ReadableRosterStore {

    /**
     * Sets the candidate roster in state.
     * Setting the candidate roster indicates that this roster should be adopted as the active roster when required.
     *
     * @param candidateRoster a candidate roster to set. It must be a valid roster.
     */
    public void putCandidateRoster(@NonNull final Roster candidateRoster);

    /**
     * Sets the Active roster.
     * This will be called to store a new Active Roster in the state.
     *
     * @param roster an active roster to set
     * @param round the round number in which the roster became active.
     *              It must be a positive number greater than the round number of the current active roster.
     */
    public void putActiveRoster(@NonNull final Roster roster, final long round);
}
```

### Configuration

The `addressbook.useRosterLifecycle` feature flag will be used to toggle between the current use of `config.txt`
based address book updates and the new roster based updates on software upgrades.

## Test Plan

### Basic Unit Tests

The platform `AddressBook` will be replaced by the `Roster` data structure in all platform unit tests.

It is difficult to perform basic unit tests of startup logic. HAPI or JRS tests will need to be created to cover
all startup scenarios:

1. Genesis
2. Normal Restart
3. Software Upgrade - All DAB tests
4. Network Transplant

### HAPI Unit Tests

Duplicate and modify the Dynamic Address Book tests to exercise the new roster life-cycle.

Develop new HAPI tests to exercise the new startup logic that is not exercised through other tests.

### JRS Tests

If the Dynamic Address Book HAPI tests are sufficient, then retire the platform's nightly DAB JRS tests.

Duplicate and modify the DAB JRS tests in the PR CI/CD pipeline.

Develop new JRS tests to exercise the new startup logic that is not exercised through other tests.

## Implementation and Delivery Plan

The long pole in the tent is the lifecycle changes that impact DevOps practices. A schedule of work to minimize
the long pole is the following implementation priority:

1. Roster Data Structure and State Management
2. Roster Lifecycle gated behind a feature flag
3. Documentation of the new roster life-cycle API for genesis and network transplant.
4. Duplication and modification of existing DAB tests to use Roster Lifecycle.
5. Development of new HAPI and JRS Tests
6. Refactor of the platform to use `Roster` instead of `AddressBook`.
7. Code Cleanup and Implementation Documentation

Manual Testing Plan:

* Once `3.` is completed above, it becomes possible to add testing labor to manually test the new startup logic on our
  test networks.

### DevOps Process Changes, in summary

#### Genesis Network

1. There must be no state present on disk to load.
2. Provide a `genesis-config.txt` file on disk in the same directory that `config.txt` is normally provided. The
   file format is the same as config.txt.
3. After startup, the `genesis-config.txt` file will be moved to a subdirectory `archive/yyyy-MM-dd_HH-mm-ss/`
   to indicate that it has been used.

#### Normal Restart

1. There must be a loadable state present on disk in the standard location.
2. There must not be a software upgrade happening.
3. Ensure that `config.txt`, `genesis-config.txt`, and `override-config.txt` are not present on disk to avoid a
   warnings in the log files. These files will be ignored.

#### Software Upgrade (No Transplant, First Upgrade With New Roster Lifecycle)

1. There must be a loadable state present on disk in the standard location.
2. There must be a software upgrade happening.
3. Provide the last `config.txt` file on disk in the same directory that `config.txt` is normally provided.
   * Following the existing DAB procedure is expected here.
4. Do not provide a `genesis-config.txt` or `override-config.txt` file on disk.
5. After startup the `config.txt` file will be moved to a subdirectory `archive/yyyy-MM-dd_HH-mm-ss/`.
6. After this release there will no longer be a `config.txt` generated on freeze upgrade shutdown.

#### Software Upgrade (No Transplant, Subsequent Upgrades)

1. There must be a loadable state present on disk in the standard location.
2. There must be a software upgrade happening.
3. Ensure that `config.txt`, `genesis-config.txt`, and `override-config.txt` are not present on disk.

#### Software Upgrade Network Transplant

1. Copy the state and PCES files from a single node in the source network and replicate them to all nodes in the target
   network.
2. There must be a loadable state present on disk in the standard location.
3. There must be a software upgrade happening.
4. Provide a `override-config.txt` file on disk in the same directory that `config.txt` is normally provided.
5. Do not provide a `genesis-config.txt` or `config.txt` file on disk.
6. After startup the `override-config.txt` file will be moved to a subdirectory `archive/yyyy-MM-dd_HH-mm-ss/`.
