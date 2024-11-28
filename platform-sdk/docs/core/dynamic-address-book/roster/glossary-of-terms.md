# Roster Glossary of Terms

![Event Window Terminology](./EventWindowTerminology.drawio.svg)
![Roster Terminology Diagram](./RosterTerminologyDiagram.drawio.svg)

## Round Terminology

### Latest Consensus Round

The `latestConsensusRound` is the most recent round to have reached consensus. Equal to the pending consensus round - 1.

### Pending Consensus Round

The `pendingConsensusRound` is the round that the consensus algorithm is currently attempting to reach consensus on.
Equal to the latest consensus round + 1.

### Rounds Non-Ancient

A setting. `roundsNonAncient` is the number of rounds that have reached consensus that are not ancient.

### Ancient

An event is ancient if its `birthRound` is less than or equal to the `latestConsensusRound` - `roundsNonAncient`.

### Minimum Round Non-Ancient

`minRoundNonAncient` is the minimum round that is considered non-ancient. This is equal to
`pendingConsensusRound` - `roundsNonAncient`.

### Event Window

Specifies the window of events that are being considered in consensus. The window specifies the following:

* `latestConsensusRound` - the latest round that has reached consensus.
* `minRoundNonAncient` - the minimum round that is considered non-ancient.

### Latest Handled Round

The `latestHandledRound` is the latest round to have been completely handled and applied to the state by the node.
Note that the application of this round to state produces a new roster to become effective after a configured
`rosterOffset`. see terminology below.

### Future Event Horizon (a.k.a. Max Gossip Round)

The `futureEventHorizon` is the maximum round for which the node has an effective roster. It is also the
maximum round that this node can gossip about. Events which have a birth round higher than the `futureEventHorizon`
will not be gossipped.

### Past Event Horizon (a.k.a. Min Gossip Round)

The `pastEventHorizon`, equal to `minRoundNonAncient`, is the minimum round for which the node has an effective roster.
It is also the minimum round that this node can receive gossip for. Events which have a birth round lower than the
`pastEventHorizon` are ancient. Ancient events are retained in gossip for a period of time to support peers that are
behind in consensus.

### Validatable Event Window (within the Event Horizon)

The `validatableEventWindow` is the window of rounds which have effective rosters that have not become ancient. It
spans, inclusively, from the `pastEventHorizon` to the `futureEventHorizon`. Events with `birthRound` within this
window are non-ancient and can be validated.

## Roster Terminology

### Roster (a.k.a. Consensus Roster)

The collection of nodes which create events and participate in consensus. Prior to the dynamic address book effort,
this was called the `AddressBook` in the platform's code. The name was changed to `Roster` to disambiguate it from
the application's concept of an address book. The application's address book contains the consensus roster.

### Effective Roster (for a round)

The roster that was used to determine consensus for a particular round. The `effective roster for round X` is the
roster that was used to compute consensus for round X.

The effective roster is used for more than just consensus:

* **Networking** - determine who to connect to and facilitate mutual TLS connection with that node.
* **Event Creation** - determine which other nodes' events are available to use as parents of events.
* **Event Validation** - validate the signature using the signing key of the node that created the event.
* **State Validation** - validating the signatures on the state of a round using the signatures in the effective
  roster for that round.

### Roster's Effective Round

The round that the roster will be used to calculate consensus for. This is the round number used to look up the
appropriate roster to validate an event. The event contains this round number as the event's `birthRound` and is
used to uniquely identify the roster that should be used to validate the event.

Not a field inside the roster.

### Roster's Creation Round

The round of transactions that created this roster once they were applied to the state.

### Roster Offset

A setting. The number of rounds it takes for a roster to become effective after being created. This is likely to be
a small integer value, e.g. 2. If this number is 2, then processing all the transactions in round 100 yields the
effective roster for round 102.

### Ancient Roster

All rosters that are effective for ancient rounds are ancient rosters.

### Minimum Roster Non-Ancient

`minRosterNonAncient` is the roster that is effective for `minRoundNonAncient`.

### Latest Handled Roster

`latestHandledRoster` is the roster that was effective and used to calculate consensus for the `latestHandledRound`.

### Latest Consensus Roster

`latestConsensusRoster` is the roster that is effective for the `latestConsensusRound`.

### Current Effective Roster

`currentEffectiveRoster` is the roster that is effective for the `pendingConsensusRound`. It is currently being
used by consensus and determines the current network topology.

### Greatest Effective Roster

`greatestEffectiveRoster` is the roster that is effective for the `futureEventHorizon`. It is the greatest roster
to have been produced by handling transactions and applying them to the state.

## Event Terminology

### Event Fields

Events have four parts to them:

1. Hashed Data
2. Signature
3. Consensus scratchpad
4. Generated by consensus

### Birth Round

`birthRound` - The pending consensus round as known by the event creator thread at the time the event was created.

This field allows nodes to determine if the event is ancient or not.

Uniquely defines the roster that should be used when validating this event. An event with birth round X should be
validated using the roster with an effective round of X.

### Consensus Round

`consensusRound` - The round in which an event reaches consensus. Previously known as `roundReceived`.

### Current Round

Used only in the consensus algorithm. Previously known as `roundCreated`. In the future, this data will
not be exposed outside of consensus.
