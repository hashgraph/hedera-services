# Dynamic Address Book - Platform Design

![Dynamic Address Book Platform Design](DynamicAddressBook-PlatformDesign.drawio.svg)

## Network Connection

### Add Node

Existing nodes need to create or receive mutual TLS gossip connections with the added node.

This change takes effect when a roster with an added node becomes the `currentEffectiveRoster` for
the `pendingConsensusRound`.

### Remove Node

Existing nodes need to stop gossiping with the removed node. Events from the removed node can still be gossiped,
validated and come to consensus until the last roster that includes the removed node becomes ancient.

This change takes effect when a roster with a removed node becomes the `currentEffectiveRoster` for
the `pendingConsensusRound`.

### Rotate Signing Key

All nodes need to tear down their existing mutual TLS connection with the node that has had its key rotated and
establish a new connection with the node using the new signing certificate as the trusted root certificate.

This change takes effect when a roster with the node's key changes becomes the `currentEffectiveRoster` for
the `pendingConsensusRound`.

### Node Weight Change

This has no effect on the existence of network connections.

## Event Creation

### Add Node

Begin using events from the node as parents of new events.

This change takes effect when a roster with an added node becomes the `currentEffectiveRoster` for
the `pendingConsensusRound`.

This change will modify network membership and the event creation algorithm will be reset.

The event creator can build on top of any event that has a birth round that is less than or equal to the event creator's
known value for the `pendingConsensusRound`. Events that have a higher birth round need to be buffered until the event
creator's known `pendingConsensusRound` becomes equal to or higher than their birth round.

Buffer: Only the latest events from each node within the same birthRound need to be buffered.

### Remove Node

When a roster with a removed node becomes the `currentEffectiveRoster` for the `pendingConsensusRound`, the event
creation algorithm is reset.

### Rotate Signing Key

The `currentEffectiveRoster` contains the key used to sign the event. Rotation of the roster will update the signing
key to use for signing self events.

### Node Weight Change

Changing network weight in the `currentEffectiveRoster` for the `pendingConsensusRound` should cause the event creation
algorithm to reset.

## Gossip

### Receiving Events

The `futureEventHorizon` and the `pastEventHorizon` define the thresholds for receiving gossipped Events. Received
events with `birthRound` outside of this node's Event Horizon will be rejected or discarded.

### Sending Events

A node will store ancient events and send them through gossip to help nodes which are behind.

### Add Node

When transaction handling creates a roster with a new node in it, it becomes the `greatestEffectiveRoster` for
the `futureEventHorizon` and this node will be able to receive these events.

### Remove Node

When a node is removed from a roster and all rosters containing the node have become ancient, this node will no longer
receive events from the removed node. If received, they will be discarded.

### Rotate Signing Key

This roster change has no effect on whether or not send or receive events through Gossip.

### Node Weight Change

This roster change has no effect on whether or not send or receive events through Gossip.

## Event Validation

For each event, lookup the roster within the `eventHorizon` that matches the `birthRound` of the event. Events
outside the `eventHorizon` are not validatable.

## Orphan Buffer

Drop ancient events that have a birthRound less than `minRoundNonAncient`.

## ISS Detection

Use the effective roster for the round in question.

## Consensus Effects

Uses the `currentEffectiveRoster` for the `pendingConsensusRound`.

When a round comes to consensus:

* The `latestConsensusRound` and `latestConsensRoster` can be retrieved from the produced `ConsensusRound`.
* A new `EventWindow` is produced on an output wire.
* Consensus advances the `pendingConsensusRound` and uses the `currentEffectiveRoster` for that round.
* The `RosterDiff` between the `currentEffectiveRoster` and the `latestConsensusRoster` is produced on an output wire.

## Signature Gathering for State

Use the effective roster for the relevant round when validating signatures on the round's state.

## Reconnect

Validating state signatures.

Use the effective roster for the relevant round when validating signatures on the round's state.

NOTE: The node must be able to validate >1/2 of the weight with its current effective roster. Since >2/3 of the
weight is needed to sign the state, a reconnecting node can tolerate 1/6 of variance in weight assigned to nodes.

## Metrics

Most metrics will need to stay in sync with the `currentEffectiveRoster` for the `pendingConsensusRound`.
