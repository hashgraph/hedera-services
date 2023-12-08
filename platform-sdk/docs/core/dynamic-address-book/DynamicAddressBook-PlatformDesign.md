# Dynamic Address Book - Platform Design

![Dynamic Address Book Platform Design](DynamicAddressBook-PlatformDesign.drawio.svg)

## Network Connection Effects

Assumption: That the network is fully connected, all nodes connect to each other.

* Need to validate that this is true.
* Huge gap in testing if not.

### Add Node

Existing nodes need to create or receive mutual TLS gossip connections with the added node.

This change takes effect when a node is added in the `currentEffectiveRoster` for the `pendingConsensusRound`.

### Remove Node

Existing nodes need to stop gossiping with the removed node. Events from the removed node can still be gossiped,
validated and come to consensus until the last roster that includes the removed node becomes ancient.

This change takes effect when a node is removed from the `currentEffectiveRoster` for the `pendingConsensusRound`.

### Rotate Signing Key

All nodes need to tear down their existing mutual TLS connection with the node that has had its key rotated and
establish a new connection with the node using the new signing certificate as the trusted root certificate.

This change takes effect when a node's key changes in the `currentEffectiveRoster` for the `pendingConsensusRound`.

### Node Weight Change

This has no effect on the existence of network connections.

## Event Creation Effects

### Add Node

Begin using events from the node as parents of new events.

This change takes effect when a node is added to the `currentEffectiveRoster` for the `pendingConsensusRound`.

This change will modify consensus weight for the network and the event creation algorithm will be reset.

Need to buffer the latest events in each round. The event creator can build on top of any event that has a
birth round that is less than or equal to the event creator's current round. Events that have a birth round higher
than the pending round need to be buffered before reaching the event creator.

Buffer: To ensure that the birth round of the child created is never less than the birth round of the parent,
only events that have a `birthRound` less than or equal to the pending consensus round, as known by the event crator, are let through.

### Remove Node

Then the `currentEffectiveRoster` for the `pendingConsensusRound` loses a node, the network weight is modified and
the event creation algorithm is reset.

### Rotate Signing Key

The `currentEffectiveRoster` contains the key used to sign the event. Rotation of the roster will update the signing
key to use.

### Node Weight Change

Changing network weight in the `currentEffectiveRoster` for the `pendingConsensusRound` should cause the event creation
algorithm to reset.

## Event Validation

For each event, lookup the roster within the `eventHorizon` that matches the `birthRound` of the event. Events
outside of the `eventHorizon` are not validatable.

## Orphan Buffer

Drop events that have a birthRound outside of the `eventHorizon`.

## ISS Detection

Use the effective roster for the round in question.

## Consensus Effects

uses the `currentEffectiveRoster` for the `pendingConsnensRound`.

When a round comes to consensus:

* The `latestConsensusRound` and `latestConsensRoster` can be retrieved from the produced `ConsensusRound`.
* A new `NonAncientEventWindow` is produced on an output wire.
* Consensus advances the `pendingConsensusRound` and uses the `currentEffectiveRoster` for that round.
* The `RosterDiff` between the `currentEffectiveRoster` and the `latestConsensusRoster` is produced on an output wire.

## Signature Gathering for State

Use the effective roster for the relevant round when validating signatures on the round's state.

## Reconnect

Validating state signatures.

Use the effective roster for the relevant round when validating signatures on the round's state.

NOTE: The node must be able to validate 1/2 of the weight with its current effective roster.  Since 2/3 of the 
weight is needed to sign the state, a reconnecting node can tolerate 1/6 of variance in weight assigned to nodes.

## Metrics

Most metrics will need to stay in sync with the `currentEffectiveRoster` for the `pendingConsensusRound`.