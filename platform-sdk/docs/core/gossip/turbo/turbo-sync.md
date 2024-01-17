# Summary

The turbo-sync algorithm is a gossip algorithm used to distribute events throughout the network. It is a variation
on the original sync gossip algorithm.

# Base Sync Algorithm

Before understanding the turbo sync algorithm, it is important to understand the base sync algorithm. The turbo sync
algorithm is equivalent to the base sync algorithm run over and over in a pipelined fashion.

The base sync algorithm has the following three phases (discounting protocol negotiation):

1) exchange tips and ancient thresholds
2) exchange booleans
3) exchange events

Each of the three phases is symmetrical. That is, the two nodes participating in a sync each perform the same
same steps.

## Phase 1: Exchange Tips And Ancient Thresholds

In the first phase of a sync, each node sends the other node its tips.

A tip is defined as an event with no self children (yet). When calculating a node's tips, do not consider events stuck
in the orphan buffer or otherwise still enqueued in the intake pipeline. We are interested in the tips of all events
currently registered in gossip's data structures used to track events.

A useful property of a node's tips is that all events known by that node will be ancestors of at least
one of the tips. (Here and elsewhere in this document, an event is considered to be an ancestor to itself.)
More on this later.

After sending tips, each node also immediately sends its ancient thresholds. This is just a description of what the
node to considers to be ancient and/or expired. Nodes will never send their peer events that the peer thinks are
ancient. This exchange of ancient thresholds is also sufficient to determine if one of the nodes has fallen behind
and needs to reconnect (not discussed in this document).

## Phase 2: Exchange Booleans

After each node sends its tips and receives the other node's tips, it then performs the second phase of a sync.

For each tip received from the other node, figure out if we currently have that event or not. If we have that event
we send `true`, if we do not have that event we send `false`. We send these responses in the same order as the tips
were received.

## Phase 3: Exchange Events

In the third and final phase of a sync, each node takes the data sent and received during previous steps, and uses it
to compute which events to send to the other node. It then sends those events.

### Computing Events to Send

First, we want to build a theory of what events the other node has. The goal is to create a set of events we know they
have so that we do not send them these events.

Start by taking the tips that the other node sent to us. If we don't have the event for a tip, we can't use it.
Discard any tips they have sent us if we don't know about the event. For the tips they sent us that we do know about,
add those tips and all non-ancient ancestors of those tips (from the peer's perspective) to the set of events the peer
is known to have.

Next, consider the tips we sent to the other node. If they responded "no I don't have that event", then we can't update
our model of their knowledge. But if they say "yes, I have that event", then we can add that tip and all non-ancient
ancestors of that tip (from the peer's perspective) to the set of events the peer is known to have.

Finally, consider all events we know about by looking at all non-ancient ancestors of the tips we sent to the peer
(from the peer's perspective). For each of these events, if the event is in the set of events known by the peer then
do not send that event. If an event is not in the set of events known by the peer, then send that event.

A nice algorithmic property of this algorithm is that for two nodes exchanging events, each node will send the other
node the exact set of events it is missing without sending any duplicate events, and vice versa. Proof of this is
left as an exercise to the reader.

Note that when a node syncs with multiple nodes in parallel there will be duplicate events. However, each honest
peer will only send each event at most once.

### Optimization: Event Filtering

An optimization that is implemented to reduce duplicate event rates in the presence of multiple parallel syncs is
to apply a filter to the events we would send to the peer during the third phase of the sync. The filter we apply is
as follows:

1) if the event is a self event, then always send it
2) if the event is an ancestor of a self event, then always send it
3) if the event is not a self event or an ancestor of a self event, then do not send it unless we have known about
   the event for a configurable period of time (e.g. a few seconds)

In a well behaved and well connected network, this will add a delay to the time it takes to retransmit other nodes'
events. This allows most nodes to receive an event directly from that event's creator, as opposed to receiving it
from multiple sources simultaneously.

# Turbo Sync Algorithm

The goal of the turbo sync algorithm is to increase the rate at which we perform a sync operation in environments with
high latency.

If protocol negotiation is included, the minimum time it takes to perform a single sync operations is 5 one way trips
between nodes, or 2.5 round trips. In a network with high latency, this can be a significant amount of time. Turbo
sync is capable of performing sync operations at a theoretical maximum rate of once sync per one way trip, or 2 syncs
in the time it takes for a round trip communication.

The way turbo sync achieves this is by running multiple sync operations with the same node simultaneously in a
pipelined fashion. This is done over a single TCP connection (i.e. not multiple parallel TCP connections with
the same peer).

Turbo sync is broken up into iterations. During each iteration, it completes a single sync operation. At any point in
time, there are three syncs in progress, each offset by one phase. During each iteration, each of the syncs in progress
completes exactly one phase.

Consider the following time line. Time moves from left to right. During each iteration, there are exactly three syncs
in progress (once we reach a steady state).

```
 --- Time --->
                             itr 1       itr 2       itr 3       itr 4       itr 5       itr 6       itr 7       itr 8

sync 1       negotiation     tips        bools       events  
sync 2                                   tips        bools       events  
sync 3                                               tips        bools       events  
sync 4                                                           tips        bools       events  
sync 5                                                                       tips        bools       events  
sync 6                                                                                   tips        bools       events  
```

During each iteration of the sync, there are no round trip communications. That is, no byte sent relies on any bytes
received during that phase of the sync. This is important in high latency environments, and this his what permits us
to perform once sync per one way trip.

## Optimization: Stateful Sync Sessions

The original sync algorithm is stateless. Sync N does not remember what happened in sync N-1. This led to additional
required throttling for the original sync algorithm in order to avoid duplicate events. If two syncs were performed one
right after the other, it was possible for some events sent in sync N-1 to still be in the peer's intake pipeline during
sync N, resulting in a duplicate event.

Turbo sync is stateful. Between two syncs, the algorithm remembers which events it has already sent, and does not send
them again.

## Optimization: Borrow Data From Adjacent Syncs

The original sync algorithm utilized the following data to build a theory of what events the peer has:

- tips received from the peer
- tips sent to the peer the responses to those tips

But in turbo sync, we have additional data we can use to shape this model. During the third phase of a sync, we have
access not only to the current sync's tips that have been exchanged, but also the tips that were exchanged in the second
phase of the following sync. These tips can be used to refine the model of what events the peer has, and to avoid
sending those events.

## Optimization: Avoiding Duplicate Tip Transmission

When syncing with a peer, if we sync very frequently, it may be the case that we have many similar tips from sync
iteration to sync iteration. We can avoid sending this duplicate data by only sending new tips, and by referring
to duplicate tips by their index in the previous sync's tip list. With this strategy, a tip is only transmitted when
it is new, and a very small placeholder is sent for syncs when the tip is not new.