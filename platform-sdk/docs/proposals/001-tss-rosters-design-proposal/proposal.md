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
This proposal attempts to provide a specification for the behavior of rosters starting from their creation from the
Candidate Address Book (CAB) to their terminal state within the TSS specification.
A roster reaches a terminal state when it is either adopted by the platform or replaced by a new roster.

The Future Address Book (FAB) is a dynamic list maintained within the App that reflects the desired future state of the
network's nodes. It will be continuously updated by HAPI transactions, such as those that create, update, or delete
nodes.
The CAB is a snapshot of the Future Address Book (FAB), and it is created when the app decides to adopt a new address
book.

This proposal specifies that one or more Roster(s) will be created from the CAB by the Hedera app and passed to the
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

### Architecture and/or Components

###### Roster Lifecycle

![](TSS%20Roster%20Lifecycle.drawio.svg)

###### Roster API

The Hedera app is already responsible for managing the address book.
We propose that it continues to do so, and at adoption time, create a candidate Roster object from the CAB and set it in
the state.
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
    PairingPublicKey tssEcPublicKey
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
It is noteworthy that the roster must be immutable to guarantee the integrity of the computed hash.
This map of rosters will typically contain the current Active Roster, an optional previously Active Roster, and an
optional current Candidate Roster.
There will be new fields in PlatformState - `candidateRosterHash` and `activeRosterHash` - such that at adoption time,
the way to trigger the adoption of a roster will be by the client code inserting a roster in the roster map, alongside
setting the `candidateRosterHash` field in the PlatformState. If a `candidateRosterHash` hash entry already exist in the
map of Rosters, it will be discarded.
The `activeRosterHash` will be private to the platform and will not be settable or modifiable from outside the platform.

This indirection avoids moving data around in the merkle state which requires data to be copied into the block stream,
which is computationally expensive.
Another benefit of this approach is that adoption trigger becomes simple (app sets the roster) with delineated
responsibilities between the app and the platform.

This map will not grow infinitely. Insertion of rosters will be controlled. The acceptance of a new Candidate Roster
will invalidate and remove the current candidate roster.

###### Roster Validity

In simple terms, the following constitutes a valid roster:

1. The roster must have at least one RosterEntry.
2. All RosterEntry/ies must have a non-zero weight.
3. All RosterEntry/ies must have a valid X509Certificate.
4. All RosterEntry/ies must have a valid PairingPublicKey.
5. All RosterEntry/ies must have at least one ServiceEndpoint.
6. All ServiceEndpoint/s must have a valid IP address, port, or domain name.
7. The roster must have a unique NodeId for each RosterEntry.

### Core Behaviors, in summary

- Roster Creation: App will create a Candidate Roster from the Candidate Address Book (CAB) and set it in the state.
- Roster Submission: App will trigger roster adoption by setting the `candidateRosterHash` field in the PlatformState.
- Roster Adoption: The platform will adopt the last submitted Candidate Roster when it is ready, based on specifications
  outlined in the TSS Ledger ID Proposal (referenced under related Proposals).
- Roster Replacement: If a new CAB is submitted before the previous one is adopted, the corresponding new Candidate
  Roster will replace the old one.

### Public API

A new method will be added to the platform API to allow the App submit a Candidate Roster to the platform:

```java
//in State

void setCandidateRoster(@NonNull final Roster candidateRoster);
```

### Configuration

The `config.txt` file will no longer be used for storing the address book in existing networks. It will only be used for
the genesis of new networks.

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
   The resolution is to get rid of Node IDs and use Node names only.


3. Decommissioning `config.txt`: This file does not have all the information that the Hedera Address Book needs (proxy
   endpoints, and in the future Block Nodes). It has verbose information for the platform. Its format could be better,
   and it stores the account number in the memo field. Upon the creation and adoption of rosters in the
   state, `config.txt` is no longer useful.
   The resolution is to offload this duty off the platform and allow Services to use whatever file format that suits
   them.

### Data storage

Most of the data related to Active and Candidate Roster will be stored in the State.
There will continue to be components used in creating the Roster - such as the private EC (elliptic curve) key, the RSA
private key and the signing X509Certificate certificate - that will be stored on disk.
However, it is up to Services to manage the lifecycle of these files, and not the platform.

There will not be any other separate artifacts stored elsewhere (e.g., directly on disk.).


### Startup procedure (pseudo-code)

The pseudocode for the startup procedure will look as follows:

```code
if(a State exists on disk) {
    loadStateFromDisk();
} else {
    roster = loadFromConfigTxt();
    // Install the roster as both Active Roster and Candidate Roster in the new state:
    createEmptyState(roster);
    // So the node will only be able to sign using its RSA key, w/o any TSS.
    isGenesis =true;
}

if(the State has Candidate Roster) {
    // Check if this is a software upgrade, and if the TSS protocol
    // has been launched already, then also check
    // if Candidate Roster has enough signatures.
    // Note that we switch to Candidate Roster during a software upgrade unconditionally
    // until the TSS protocol is actually launched.
    if(isSoftwareUpgrade /* && Candidate Roster is complete */) {

        // This MUST be performed under `isSoftwareUpgrade` to ensure that
        // the entire network is being restarted, and so every node adopts the Candidate Roster,
        // and hence no ISSes happen.
        // Modify the state and put Candidate Roster into Active Roster, effectively clearing the Candidate Roster.
        makeCRtheAR();
        // May make a record of the previous Active Roster if necessary (e.g. for PCES replay)
    }

	/*
	// This block will be uncommented once the TSS protocol is implemented
	if (Candidate Roster is not complete) {
		// Call the "TSS State Observer" to make it initiate the TSS protocol.
		// See the Detecting a new Candidate Roster (aka the new “Platform API”) section below.
		callTSSStateObserverToStartTSSProtocol();
		// NOTE: processing of TSS messages will emit a percentage metric indicating the readiness
		// of the Candidate Roster for adoption. Once the metric is at 100%, this shows
		// that the network can be restarted in order to adopt the Candidate Roster.
	}
	*/
}

if(the State has no Active Roster) {

    // This should never happen
    throwFatalErrorAndShutdown();
}

// Check if the Active Roster is TSS-enabled
if(Active Roster is not TSS-enabled) {
    /*
        // This block will be uncommented once the TSS protocol is implemented
        if (isGenesis) {
            rejectAnythingButTSSMessages = true;
            // Note that a TSS upgrade of an existing network
            // shouldn't disable non-TSS messages processing.	
        }
    */

    // In Genesis, we've just disabled processing anything but TSS. So a new
    // network will eventually become TSS-enabled and only then will start
    // processing non-TSS events.
    // An existing, non-genesis network will continue to operate as before,
    // and if a Candidate Roster was present in the state, we enabled processing TSS messages
    // above. So it will become TSS-enabled eventually, too, indicating
    // the readiness for a TSS upgrade via the metric mentioned above.
    // Note that until a Candidate Roster is installed in the state, the existing network
    // will simply continue to operate as usual with its regular RSA keys.
}

// At this point the Active Roster in the state is what we'll be using as a roster.
// If an incomplete Candidate Roster exists, we've started exchanging TSS messages above.
// If it's genesis, we've disabled processing anything but TSS messages above.
// If Active Roster is already TSS-enabled (a mature network running after the TSS
// upgrade), then the node will be able to sign using TSS.
// If Active Roster isn't TSS-enabled yet, then the node will sign using its RSA key only,
// just as it does today.
```

### Roster changes needed for Components

- Reconnect. Reconnect logic currently exchanges and validates address book between the learner and teacher nodes. The
  learner node uses the address book to select the teacher node, as well as remove all invalid signatures from the
  state. Both of these will need to be updated to use rosters instead.
- Networking. Most parts of the network code have been abstracted away from the address book as part of the Dynamic
  Address Book effort, so there should be minimal work left there.
- Event Validation. The event validation logic uses the address book to determine whether a given event has a valid
  signature. This will be updated to use rosters instead.
- Miscellaneous components. Other components that reference things stored in the address book (like consensus needing
  the node weight or the crypto module verifying keys) will need to be updated to use things stored in rosters instead.

### Test Plan

Some of the obvious test cases to be covered in the plan include validating one or more of the following scenarios:

1. New valid Candidate Roster created with no subsequent one sent by app. Verify accept.
2. New valid Candidate Roster created with subsequent one sent by app. Verify accept.
3. Invalid roster(s) sent by the app. Verify reject.
4. Empty roster(s) sent by the app. Verify reject.
5. Node Failures During Roster Change: What happens if nodes fails or disconnects during a roster change? Verify valid
   node successfully reconnects.
6. Concurrent Roster Updates: What if we make multiple roster updates concurrently? Verify no effect on adoption.
8. Roster recovery? Node receives candidate roster, crashes. Wakes up, reconnects. Verify recovery.
9. What testing do we need for genesis new network?
10. What end to end testing do we need for brand new network that uses the TSS signature scheme to sign its blocks?

### Metrics

We propose that some metrics be added such as `createTimestamp` of a Candidate Roster, and the time the roster was
either adopted or discarded.

### Implementation and Delivery Plan

- Define Roster Data Structure and API: Design and implement the Roster class and the associated API methods.
- Modify State Storage: Update the state to store the map of rosters and the candidateRosterHash and activeRosterHash
  fields.
- Implement Roster Adoption Logic: Develop the logic for the platform to adopt candidate rosters based on TSS readiness
  and voting.
- Update Reconnect Process: Modify the reconnect process to use rosters instead of address books.
- Testing and Deployment: Conduct thorough testing of the new roster implementation and deploy.