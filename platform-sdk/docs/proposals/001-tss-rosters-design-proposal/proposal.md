# TSS - Roster Proposal

---

## Summary

This proposal outlines the design specification of rosters to support Threshold Signature Scheme (TSS) block signing.
It details the roster's lifecycle, data storage, API, and necessary changes to associated components within the
platform.

| Metadata           | Entities                                      | 
|--------------------|-----------------------------------------------|
| Designers          | Kore, Anthony, Ed, Cody, Austin               |
| Functional Impacts | TSS, Genesis, Cryptography, Reconnect         |
| Related Proposals  | TSS-Library, TSS-Ledger-Id, TSS-Block-Signing |
| HIPS               | N/A                                           |

## Purpose and Context

The introduction of the Threshold Signature Scheme (TSS) requires a new mechanism for managing node participation in
consensus and block signing.
The Roster, an immutable subset of the address book, will provide this mechanism, ensuring efficient and secure key
management for TSS operations.
This proposal provides a specification for the behavior of rosters starting from their creation from the Address Book to
their terminal state within the TSS specification.
A roster reaches a terminal state when it is either adopted by the platform or replaced by a new roster.

The app will maintain a version of the Address Book as a dynamic list that reflects the desired future state of the
network's nodes. This dynamic list of Addresses will be continuously updated by HAPI transactions, such as those that
create, update, or delete nodes.
At some point when the app decides to adopt a new address book, it will create something called a `Roster` with
information from the Address Book and pass it to the app.

This proposal specifies that a Roster will be created from the CAB by the Hedera app and passed to the
platform code.
The mechanism for doing so is detailed below.

### Requirements

- Immutability: Rosters must be immutable to protect the integrity of its computed hash.
- Efficient Storage: Roster data should be stored efficiently in the state to minimize overhead and unnecessary data
  copying.
- Clear API: A well-defined API should be provided for the app to create and submit candidate rosters, and for the
  platform to manage the lifecycle of submitted rosters.
- Components update: Platform components that currently rely on the use of the Address book (such as Reconnect, Network,
  Event Validation etc.) must be adapted to use rosters instead.
- Address book paradigm discontinued within the platform codebase, only use rosters instead

### Architecture

###### Roster Lifecycle

![](TSS%20Roster%20Lifecycle.drawio.svg)

### Data Structure

###### Roster Public API

A new method will be added to the platform state to allow the App to submit a Candidate Roster to the platform:

```java
//in PlaformState

void setCandidateRoster(@NonNull final Roster candidateRoster);
```
The Hedera app is already responsible for managing the address book.
We propose that it continues to do so, and when it receives a HAPI transaction, creates a candidate Roster object
and set it in the state via the new API.
The State will contain one map of rosters, keyed by roster’s hash - ```Map<Hash<Roster>, Roster>```.

A Roster has a data structure as follows:

```
class Roster {
    List~RosterEntry~ entries
}

class RosterEntry {
    NodeId nodeId
    long weight
    X509Certificate gossipCaCertificate
    PairingPublicKey tssEncryptionKey
    List~ServiceEndpoint~ gossipEndpoints
}

class ServiceEndpoint {
    bytes ipAddressV4
    int32 port
    string domain_name
}

Roster "1" *-- "many" RosterEntry
RosterEntry "1" *-- "many" ServiceEndpoint

```

###### Roster Protobuf

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
     * This list SHALL contain roster entries in natural order of ascending node ids.
     * This list SHALL NOT be empty.<br/>
     */
    repeated RosterEntry rosters = 1;
}
```

A roster entry will look as follows:
```proto
/**
 * A single roster in the network state.
 *
 * Each roster entry in the roster list SHALL encapsulate the elements required
 * to manage node participation in the Threshold Signature Scheme (TSS).<br/>
 * All fields are REQUIRED.
 */
message RosterEntry {

    /**
     * A consensus node identifier.
     * <p>
     * Node identifiers SHALL be unique _within_ a shard and realm,
     * but a node SHALL NOT, ever, serve multiple shards or realms,
     * therefore the node identifier MAY be repeated _between_ shards and realms.
     */
    uint64 node_id = 1;

    /**
     * A consensus weight.
     * <p>
     * Each node SHALL have a weight of zero or more in consensus calculations.<br/>
     * The consensus weight of a node SHALL be calculated based on the amount
     * of HBAR staked to that node.<br/>
     * Consensus SHALL be calculated based on agreement of greater than `2/3`
     * of the total `weight` value of all nodes on the network.
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
     * An elliptic curve public encryption key.<br/>
     * This contains the _long term_ public key for this node.
     * <p>
     * This value SHALL be the DER encoding of the presented elliptic curve
     * public key.<br/>
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
     * This list SHALL NOT contain more than `10` entries.<br/>
     * The first two entries in this list SHALL be the endpoints published to
     * all consensus nodes.<br/>
     * All other entries SHALL be reserved for future use.
     */
    repeated proto.ServiceEndpoint gossip_endpoint = 5;
}
```

The map of rosters will be stored in the state as a virtual map of Key `hash` and value `Roster`.
The `hash` will be an implementation of the `VirtualKey` interface that supports a hash value of type `byte`.

The `Roster` value is modeled as previously shown.

The platform state protobuf will be updated to include the roster hashes.

```proto
/**
 * The current state of platform consensus.<br/>
 * This message stores the current consensus data for the platform
 * in network state.
 *
 * The platform state SHALL represent the latest round's consensus.<br/>
 * This data SHALL be used to ensure consistency and provide critical data for
 * restart and reconnect.
 */
message PlatformState {

    //... existing entries omitted for brevity ...

    /**
     * The SHA-384 hash of a candidate roster.
     * <p>
     * This is the hash of the roster that is currently being considered
     * for adoption.
     * A Node SHALL NOT, ever, have more than one candidate roster
     * at the same time.
     */
    byte candidate_roster_hash = 6;

    /**
     * The SHA-384 hash of an active roster
     * This is the hash of the roster that has already been adopted by the
     * network.
     * A Node SHALL NOT, ever, have more than one active roster
     * at the same time.
     */
    byte active_roster_hash = 7;
}
```

It is noteworthy that the roster must be immutable to guarantee the integrity of the computed hash.
This map of rosters will typically contain the current Active Roster, an optional previously Active Roster, and an
optional current Candidate Roster. Insertion of rosters will be controlled by ensuring that the acceptance of a new
Candidate Roster invalidates and removes the current candidate roster. Therefore, the map is expected to have a maximum
size of three elements.
There will be new fields in PlatformState - `candidateRosterHash` and `activeRosterHash` - such that at adoption time,
the app will set the roster through the API and the platform code inserting this roster into the roster map, alongside
setting the `candidateRosterHash` field in the PlatformState. If a `candidateRosterHash` hash entry already exist in the
map of Rosters, it will be discarded. That is, setting a candidate roster is an idempotent operation.
The `activeRosterHash` will be private to the platform and will not be settable or modifiable from outside the platform.

This indirection (using a Map instead of a Singleton) avoids moving data around in the merkle state which requires data
to be copied.
When a map is updated, only the key-value pair that changes needs to be added to the block stream.
Another benefit of this approach is that adoption trigger becomes simple (app sets the roster) with delineated
responsibilities between the app and the platform.


###### Roster Validity

In simple terms, the following constitutes a valid roster:

1. The roster must have at least one RosterEntry.
2. At least one RosterEntry/ies must have a non-zero weight.
3. All RosterEntry/ies must have a valid X509Certificate.
4. All RosterEntry/ies must have a valid PairingPublicKey.
5. All RosterEntry/ies must have at least one gossip Endpoint.
6. The RosterEntry/ies must be specified in order of ascending node id.
7. All ServiceEndpoint/s must have a valid IP address or domain name (mutually exclusive), and port.
8. The roster must have a unique NodeId for each RosterEntry.

Note that a roster can be valid, but not accepted by the platform.
For example, if a new candidate roster is set via the API, but its hash evaluates to the hash of the existing
candidate roster, the new roster will be discarded. The operation has no effect.

### Startup procedure, Services and DevOps Workflow changes

#### Startup changes

On startup, the presence or absence of an Active Roster in the state will be used to determine whether the platform
should keep existing settings,
start a genesis network process, or start the Network Transplant Process as shown.
![](TSS%20Roster%20Startup%20Behavior.drawio.svg)

The pseudocode for the startup procedure will look as follows:

```java
/**
 * Start the platform.
 * @param state an initial state. The caller either loads it from disk if the node has run before,
 *              or constructs a new empty state and stores the genesis roster in there
 *              as the current active roster.
 */
void startPlatform(final State state) {
    if (the State has Candidate Roster){
        if (isSoftwareUpgrade) {
            // This MUST be performed under `isSoftwareUpgrade` to ensure that
            // the entire network is being restarted, and so every node adopts the Candidate Roster,
            // and hence no ISSes happen.
            // Modify the state and put Candidate Roster into Active Roster, effectively clearing the Candidate Roster.
            makeCRtheAR();
            // May make a record of the previous Active Roster if necessary (e.g. for PCES replay)
        }
    }

    if (the State has no Active Roster){
        // This should never happen, but we have to check this because we're given a state as an argument.
        throwFatalErrorAndShutdown();
    }

    // At this point the Active Roster in the state is what we'll be using as a roster.
}
```

#### Services changes (Inversion of control)

The current startup procedure will be altered as follows

- The `config.txt` file will no longer be used by the platform for storing the address book in existing networks. It
  will no longer be used by the platform code
- Designating `config.txt` introduces inversion of control. It will be at the exclusive prerogative of Services (the
  app) going forward. All the platform code that builds an AddressBook from `config.txt` will be removed or refactored
  and moved into the Services codebase.

#### DevOps Workflow changes

(See Roster Startup Behavior diagram above)
When adding new nodes to existing networks, DevOps will be given a state after the network has upgraded.
The state contains a combination of optional State data and Roster with the new node in it. Devops will no longer
need `config.txt` on new nodes to existing networks.
Although the State and Roster are tagged as optional, one of them must exist for the network to start.

### Core Behaviors, in summary

- Roster Creation: App will create a Candidate Roster from the Candidate Address Book (CAB).
- Roster Submission: App will trigger roster submission by setting the `candidate_roster_hash` field in the
  PlatformState.
- Roster Adoption: The platform will vote to adopt the last submitted Candidate Roster when it is ready, which will be
  on software upgrade.
- Roster Replacement: If a new candidate roster is submitted before the previous one is adopted, the corresponding new
  Candidate Roster will replace the old one.


### Dependencies

There are a few existing technical debts that the team has agreed to tackle as dependencies for this proposal.

1. Source of Node identity: Nodes infer their node id from the internal hostname matching the address entry in the
   address book. Two nodes on different corporate networks may have the same internal ip address, ex: 10.10.10.1. While
   highly improbable, we’re lucky there hasn’t been a collision so far. The result would be that the software would try
   to start both nodes on the same machine.
   The resolution is that nodes should get their identity (Node Id) specified explicitly through the node.properties
   file.


2. Off-by-1 problem: Node IDs are 1 less than the node name. For example, the name of the node with node id 0
   is `node1`. This is confusing. The node name is used as the alias in the cryptography and used to name the pem files
   on disk. Node id 0 gets its cryptography through “node1” alias.
   The resolution is to get rid of node names and use Node IDs only.


3. Inversion of Control: The `config.txt` file does not have all the information that the Hedera Address Book needs (
   proxy endpoints, and in the future, Block Nodes). It has verbose information for the platform. Its format could be
   better,
   and it stores the account number in the memo field. Upon the creation and adoption of rosters in the
   state, `config.txt` is no longer useful.
   The resolution is to offload this duty off the platform and allow Services to use whatever file format that suits
   them as described earlier.

### Data storage

All of the data related to Active and Candidate Roster will be stored in the State.
There will continue to be components used in creating the Roster - such as the private EC (elliptic curve) key, the RSA
private key and the signing X509Certificate certificate - that will be stored on disk.
However, it is up to Services to manage the lifecycle of these files, and not the platform.

There will not be any other separate artifacts stored elsewhere (e.g., directly on disk.).


### Roster changes needed for Components

#### Block Proof

We propose the introduction of a queue of roster hashes stored in the state, which holds the current and previous active
rosters.
In this proposal implementation, this queue will contain only 2 hashes, with the first hash representing
the previous active roster, and the second hash representing the current active roster.
At upgrade boundaries, we will pop off the previous active roster and add a new roster to the end of the queue which
becomes the new active roster as shown below.
![](TSS%20Roster%20Lifecycle-Roster%20and%20Rounds.drawio.svg)

This approach provides the benefit of introducing the necessary data structure for full DAB (to be detailed in a future
design proposal),
while also providing a mechanism for tracking the active roster.

#### Reconnect

Reconnect. Reconnect logic currently exchanges and validates the address book between the learner and teacher nodes. The
  learner node uses the address book to select the teacher node, as well as remove all invalid signatures from the
  state. Both of these will need to be updated to use rosters instead.

#### Others
- Networking. Most parts of the network code have been abstracted away from the address book as part of the Dynamic
  Address Book effort, so there should be minimal work left there.
- Event Validation. The event validation logic uses the address book to determine whether a given event has a valid
  signature. This will be updated to use rosters instead.
- Miscellaneous components. Other components that reference things stored in the address book (like consensus needing
  the node weight or the crypto module verifying keys) will need to be updated to use things stored in rosters instead.

### Test Plan

Some of the obvious test cases to be covered in the plan include validating one or more of the following scenarios:

1. New valid Candidate Roster created with no subsequent one sent by app. Verify accept.
2. New valid Candidate Roster created with the subsequent one sent by app. Verify accept.
3. Invalid roster(s) sent by the app. Verify reject.
4. Node Failures During Roster Change: What happens if nodes fail or disconnect during a roster change? Verify valid
   node successfully reconnects.
5. Concurrent Roster Updates: What if we make multiple roster updates concurrently? Verify no effect on adoption.
6. Roster recovery? The Node receives a candidate roster, crashes. Wake up, reconnect. Verify recovery.
7. What end-to-end testing do we need for a brand-new network that uses the TSS signature scheme to sign its blocks?

### Metrics

We propose that some metrics be added. One useful metric we will
introduce is the number of candidate rosters that have been set. Others may be introduced during implementation.

### Implementation and Delivery Plan

- Define Roster Data Structure and API: Design and implement the Roster class and the associated API methods.
- Modify State Storage: Update the state to store the map of rosters and the candidateRosterHash and activeRosterHash
  fields.
- Implement Roster Adoption Logic: Develop the logic for the platform to adopt candidate rosters based on TSS readiness
  and voting.
- Update Reconnect Process: Modify the Reconnect process to use rosters instead of address books.
- Testing and Deployment: Conduct thorough testing of the new roster implementation and deploy.