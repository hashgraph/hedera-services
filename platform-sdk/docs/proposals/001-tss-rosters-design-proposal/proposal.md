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
Roster, an immutable subset of the address book, will provide this mechanism, ensuring efficient and secure key
management for TSS operations.
This proposal attempts to provide a specification for the behavior of rosters starting from their creation from the
Candidate Address Book (CAB) to their terminal state within the TSS specification.
A roster reaches a terminal state when it is either adopted by the platform or replaced by a new roster.

The Future Address Book (FAB) is a dynamic list maintained within the App that reflects the desired future state of the
network's nodes. It will be continuously updated by HAPI transactions, such as those that create, update, or delete
nodes.
The CAB is a snapshot of the Future Address Book (FAB), and it is created when the app decides to adopt a new address
book.

This proposal specifies that Roster(s) will be created from the CAB by the Hedera app and passed to the platform code.
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
    X509Certificate signingCert
    PairingPublicKey tssEcKey
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
This map of rosters will typically contain the current Active Roster, an optional previously Active Roster, the optional
previous Candidate Roster (a roster created from a candidate address book), and an optional current Candidate Roster.
There will be new fields in PlatformState - `candidateRosterHash` and `activeRosterHash` - such that at adoption time,
the way to trigger the adoption of a roster will be by the client code inserting a roster in the roster map, alongside
setting the `candidateRosterHash` field in the PlatformState. If a `candidateRosterHash` hash entry already exist in the
map of Rosters, it will be discarded.
The `activeRosterHash` will be private to the platform and will not be settable or modifiable from outside the platform.

This indirection avoids moving data around in the merkle state which requires data to be copied into the block stream,
which is computationally expensive.
Another benefit of this approach is that adoption trigger becomes very simple (app sets the roster) with delineated
responsibilities between the app and the platform.

Some edge cases worth considering include:

1. multiple concurrent roster submission - has a potential for introducing race conditions. We will prevent this by
   guaranteeing immutability on rosters.
2. Size control on the map of rosters - we certainly don’t want this map to grow infinitely so insertion of rosters will
   be controlled, with clear rules for removal of unused rosters - the acceptance of a new Candidate Roster
   will invalidate and remove the current candidate roster.

###### Roster API - Implementation

Implementation of some new TSS components has been proposed.
See [link](https://www.notion.so/TSS-Platform-Architecture-04b15df371ba4b1d848360542e05a030?pvs=21).

It is **important** to note that before the TSS protocol is implemented and launched, the Platform will adopt a
Candidate Roster during any software upgrade if the Candidate Roster is present in the state. Only after the TSS
protocol is launched will we be able to add an extra condition to verify that the Candidate Roster is ready to be
adopted.

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

### Data storage

All the data related to Active and Candidate Roster will be stored in the State.
There will not be any other separate artifacts stored elsewhere (e.g., directly on disk.).
The only artifact that will continue to be stored on disk separately from the State is the `config.txt`, which, as
explained in *Bootstrapping Genesis for a brand-new network* section below, will only ever be used once during a genesis
of a brand-new network. The `config.txt` file, or at least its part that describes the address book components, will
never be used in the life cycle of an existing network after that.

### Startup procedure (pseudo-code)

The pseudocode for the startup procedure will look as follows:

```java
if(a State
exists on
disk){

loadStateFromDisk();
}else{
roster =

loadFromConfigTxt();

// Install the roster as both Active Roster and Candidate Roster in the new state:
createEmptyState(roster);
// So the node will only be able to sign using its RSA key, w/o any TSS.
isGenesis =true;
        }

        if(
the Statehas
Candidate Roster){
        // Check if this is a software upgrade, and if the TSS protocol
        // has been launched already, then also check
        // if Candidate Roster has enough signatures.
        // Note that we switch to Candidate Roster during a software upgrade unconditionally
        // until the TSS protocol is actually launched.
        if(isSoftwareUpgrade /* && Candidate Roster is complete */){

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

            if(
the State
has no
Active Roster){

// This should never happen
throwFatalErrorAndShutdown();
}

// Check if the Active Roster is TSS-enabled
        if(
Active Roster
is not
TSS-enabled){
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

### Bootstrapping Genesis for a brand new network

This proposal already creates a new interface for the services to install a Candidate Roster—via a Platform API, or
similar—which ultimately stores the Candidate Roster in a designated location in the State.
The Startup procedure described above consumes the Candidate Roster from that location and starts its processing or
adoption based on its readiness.

The `config.txt` will only ever be used literally once—when a brand new network goes through its genesis.
The `config.txt` will never be used again in the network life cycle.

Given that:

1. we need a special mechanism for supplying a genesis roster to a brand new network, and
2. this mechanism is only ever used once during genesis, and
3. we already have a support for `config.txt` in the platform, and
4. we already propose a public interface for Services to install Candidate Rosters in existing networks

there should be absolutely no need to introduce any additional mechanisms or interfaces between Platform and Services to
supply a genesis roster.

If we must, we may add a field to the `config.txt` to indicate the number of shares for each node.
This should be a relatively trivial change.
However, since the `config.txt` would only ever be used once during genesis, it’s a lot simpler to skip that and assume
equal shares for all genesis nodes, and only start supporting non-equal shares when a new Candidate Roster is installed
via the new public interface of the Platform API. But as mentioned, if we must support this at genesis for brand new
networks, we can do that, and it should be trivial.

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
3. Invalid roster(s) sent. Verify reject.
4. Empty roster(s) sent. Verify reject.
5. Node Failures During Roster Change: What happens if nodes fails or disconnects during a roster change? Verify valid
   node successfully reconnects.
6. Concurrent Roster Updates: What if we make multiple roster updates concurrently? Verify no effect on adoption.
7. Rosters with nodes with zero or maximum shares. What happens?
8. Roster recovery? Node receives roster, crashes. Wakes up … ?
9. What testing do we need for genesis new network?
10. What testing do we need for an already new network with no previous TSS upgrade
11. What testing do we need for an already new network with previous TSS upgrade

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