# Summary

The turbo-sync algorithm is a gossip algorithm used to distribute events throughout the network. It is a variation
on the original sync gossip algorithm.

# Base Sync Algorithm

Before understanding the turbo sync algorithm, it is important to understand the base sync algorithm. The turbo sync
algorithm is equivalent to the base sync algorithm run over and over in a pipelined fashion.

The base sync algorithm has the following three phases (discounting protocol negotiation):

1) exchange tips
2) exchange booleans
3) exchange events

Each of the three phases is symmetrical. That is, the two nodes participating in a sync each perform the same
same steps.

## Phase 1: Exchange Tips

In the first phase of a sync, each node sends the other node its tips.

A tip is defined as an event with no children (yet). When calculating a node's tips, do not consider events stuck in
the orphan buffer or otherwise still enqueued in the intake pipeline. We are interested in the tips of all events
currently registered in gossip's data structures used to track events.

A useful property of a node's tips is that all events known by that node will be ancestors of at least
one of the tips. (Here and elsewhere in this document, an event is considered to be an ancestor to itself.)


### Old Sync Tip Definition



## Phase 2: Exchange Booleans
## Phase 3: Exchange Events