# Consensus Glossary

Note: this document is written assuming we are using birth rounds instead of generations to define
when an event becomes ancient.

## Ancient Event

In plain words, an ancient event is an event that is old enough that we can stop caring about it. More formally,
an event is considered to be ancient if its birth round is less than or equal to the
[minimum non-ancient birth round](#minimum-non-ancient-birth-round).

When computing consensus for a round, all ancient events are ignored.

## Birth Round

When a new event is created, it is assigned a birth round. A new event's birth round is equal to the
[pending round](#pending-consensus-round) at the moment of its creation.

### Invariant: Birth Rounds Never Decrease

A child event is required to have a birth round that is not less than the birth round of any of its parents.

### Claimed Parent Birth Rounds

Each event must make a claim about the birth round of each of its parents, and that claim must be included in the
hashed bytes of the event.

If the event's claimed parents cause the event to violate the invariant that birth rounds never decrease, then the
event is immediately discarded as invalid.

The reason why the word "claimed" is used is because it's possible for a malicious node to lie about the existence
of a parent that may not exist. The fact that a claimed parent birth round may not be correct is not a problem though.

- if an attacker claims to have a parent that doesn't exist, we hold the child event in the orphan buffer until
  the non-existent parent is ancient
- if the attacker claims a parent has the wrong birth round and the parent exists, we detect the discrepancy and do not
  link the child to the misconfigured parent. In this situation, it is as if the parent-child link was never created.

## Branching

FUTURE WORK: write down the technical definition

## Consensus Generation

When a round reaches consensus, all events in that round are assigned a consensus generation. For round `R`, use the
following algorithm to assign each event a consensus generation:

- iterate over events in topological order (this can be achieved by sorting on preconsensus generation)
- for each event, assign the consensus generation as the maximum of the consensus generation of its parents plus 1
- if an event has no parents, assign it a consensus generation of 1
- for all parent events that did not reach consensus in round `R`, ignore them when for the purpose of this calculation

Round generations are guaranteed to be deterministic across all nodes.

The consensus generation is used as a tie breaker when determining the consensus order of events in a round. This
provides the guarantee that if event A and event B both reach consensus and A is a parent of B, then A will reach
consensus before B.

After computing the consensus order for a round, the consensus generation of an event is no longer needed and is

## Expired Event

Analogous to the concept of an [ancient event](#ancient-event), an expired event is an event that is old enough that
we are no longer interested in gossiping it. Not all ancient events are expired events, but all expired events are
ancient events. It would technically be legal to consider all ancient events to be expired events, but we choose not
to in order to effectively gossip with peers that may be slightly behind us.

The exact computation for when an event becomes expired is an implementation detail of gossip algorithms. Any
algorithm that satisfies the above definition is acceptable as long as the gossip algorithm is happy with the result.

## Future Event

An event is considered to be future event if its birth round is greater than the
[pending round](#pending-consensus-round).

When choosing events to use as parents for a new self event, future events are never selected. This is required to
maintain the [invariant that birth rounds never decrease](#invariant-birth-rounds-never-decrease).

### Unverifiable Future Event

A future event is considered to be unverifiable future event if we do not yet know what the roster is going to be
during that event's birth round. (The roster for an event's birth round is necessary to determine the validity of
that event.) Nodes always reject unverifiable future events.

### Verifiable Future Event

A future event is considered to be verifiable future event if we know what the roster is going to be during that event's
birth round. (The roster for an event's birth round is necessary to determine the validity of that event.)
Verifiable future events are accepted through gossip, but are not added to the hashgraph until they are no
longer future events.

## Judge

Another term for a [unique famous witness](#unique-famous-witness).

## Latest Consensus Round

The latest round that has fully reached consensus.

## Minimum Non-Ancient Birth Round

To compute the current minimum non-ancient birth round, look at the
[minimum non-ancient round](#minimum-non-ancient-round). Within that round, find the judge with the lowest birth round.
That birth round is the minimum non-ancient birth round.

Any event that has a birth round greater to or equal to the minimum non-ancient birth round is considered to be
[non-ancient](#non-ancient-event). Events with a birth round less than the minimum non-ancient birth round are
considered to be [ancient](#ancient-event).

## Minimum Non-Ancient Round

The minimum non-ancient round number is defined as the
([latest round](#latest-consensus-round) - [rounds non-ancient](#rounds-non-ancient)) + 1).

## Non-Ancient Event

The inverse of an [ancient event](#ancient-event). An event is considered to be non-ancient if its birth round is
greater than or equal to the [minimum non-ancient birth round](#minimum-non-ancient-birth-round).

When computing consensus for a round, we are only permitted to consider non-ancient events, and we must know about
all non-ancient ancestors of an event before we can consider that event.

## Pending Consensus Round

The round that we are currently working on.  Equal to the [latest round](#latest-consensus-round) plus one.

## Rounds Non-Ancient

A configuration parameter for the network. Historically on the Hedera network, this value has been set to 26.

## Preconsensus Generation

The preconsensus generation of an event is defined as the maximum generation of all of that event's parents plus 1.
If an event's has an ancient parent, then the that parent is ignored for the purpose of this calculation.
Events without parents are assigned a generation of 1.

This computation is not deterministic from node to node, and likely will not be deterministic on a node after it
restarts.

The following algorithms utilize the preconsensus generation:

- the tipset event creation algorithm
- the sync gossip algorithm
- computing the [consensus generation](#consensus-generation)

## Stale Event

An event is considered to be stale if it becomes ancient before it reaches consensus. Once an event becomes stale,
it will never be able to reach consensus.

## Unique Famous Witness

Another term for a [judge](#judge).

A famous witness is unique if there are not two famous witnesses in the same round that are created by the same node.

### Invariant: Prerequisite Unique Famous Witnesses

In order to compute consensus for round R, all unique famous witnesses for round R-1 must be in the hashgraph.

## Witnesses

The first event created by a node in a particular [round](#round) is considered to be a witness. As long as a node is
branching, it will only create one witness per round.

# FUTURE WORK

Terms we have but haven't had a chance to write up yet.

## Famous Witness
## Round
## Round Created
## Event
### Child Event
### Parent Event
### Self Parent
### Other Parent
## Election
## Fallen Behind
## Gap
## Gossip
## Hashgraph
## Keystone Event
## Node
## Quiescence
## Reconnect
## Roster
## Consensus
## See
### See Through
### Strong See
### Strongly See Through
## Threshold
### Strong Minority
### Majority
### Supermajority
## Topological Order
## Voting
## Whitening