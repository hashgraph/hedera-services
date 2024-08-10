# Consensus Layer of the Consensus Node

---

## Summary

Update the architecture for the consensus node to reduce complexity, improve performance, and improve stability. This
design document defines the next iteration of the consensus node architecture, especially as related to consensus.

| Metadata           | Entities                                                 | 
|--------------------|----------------------------------------------------------|
| Designers          | Richard Bair, Jasper Potts, Oleg Mazurov, Austin Littley |
| Functional Impacts | Consensus Node                                           |

---

## Purpose and Context

Much of the motivation for this design can come down to paying down technical debt and simplifying the overall design.
While the current design is full of amazing high quality solutions to various problems, the overall system is more
complex than necessary, leading to hard-to-find or predict bugs, performance problems, or liveness (stability) issues
while under load. In addition, the separation of the "platform" and "services" in the consensus node is somewhat
arbitrary, based on and abstract concept of what the "platform" was long before Hedera was even created. This work is
also necessary to prepare for more autonomous node operation, and community nodes.

Principally:
1. This design defines several high-level modules, made up of internal "components". Whereas the current implementation
   only has a logical grouping of components into systems, this design has concrete modules providing a strong
   modularization and isolation with strict contracts between modules, leading to an overall simpler to understand
   system, less prone to "spaghetti".
2. Each module has very well-defined inputs and outputs.
3. Assumptions and requirements that led to tight coupling between modules have been revisited, and where possible,
   eliminated.
4. The concepts of "platform" vs. "services" is less important in this design than the difference between "consensus"
   and "execution". The Consensus module takes transactions and produces rounds. Everything required to make that happen
   (gossip, event validation, hashgraph, event creation, etc.) is part of the Consensus module. It is a library, and
   instances of the classes and interfaces within this library are created and managed by the Execution module. The
   Consensus module has no state, no main method, and minimal dependencies.
5. The Execution module is a mixture of what we have called "services" and some parts of "platform". The services code
   has a `HandleWorkflow` and the platform has a number of classes that handle "post-consensus" responsibilities such
   as managing the merkle tree, making fast copies of it, hashing it, etc. These responsibilities will be merged into
   the `HandleWorkflow`, dramatically simplifying the interface and boundary between "consensus"/"platform" and
   "execution"/"services".
6. Backpressure, or dealing with a system under stress, will be radically redesigned based on [PID](https://en.wikipedia.org/wiki/Proportional%E2%80%93integral%E2%80%93derivative_controller)
   controller logic, and known as "dynamic throttles". The key concept is that we primarily throttle using information
   from _the entire network_, rather than throttling only based on information available to the Consensus module like we
   do today.

The purpose of this document is not to describe the implementation details of each of the different modules. Nor does
it go into great detail about the design of the execution layer (which is primarily documented elsewhere). Instead,
it provides an overview of the whole system, with an emphasis on the Consensus module, and how the Consensus module
interacts with the Execution module.

For example, the responsibility for reconnect moves in this design from Consensus to Execution. The details on how the
"app-base" module in the services code will be updated to support reconnect is not covered in this document, but the
high-level description of why reconnect is moved to Execution, and how it impacts Consensus, will be covered.

### Dependencies, Interactions, and Implications

This document supports existing features implemented in new ways, and it provides for new features (such as the dynamic
address book) which have not been implemented. After acceptance, a long series of changes will be required to modify
the existing codebase to meet this new design. This will not happen overnight, nor will it block progress on all other
initiatives. Instead, this plan provides the blueprint for our new node architecture, which we will work towards
implementing with every change we make going forward. This blueprint will also provide the framework within which we
will evaluate all other feature designs and implementations.

## Design

TODO: THIS IMAGE MUST BE UPDATED
![Design](consensus-node-mid-level-arch.png)

The consensus node is made up of two parts, a Consensus layer, and an Execution layer. Each layer is represented by
JPMS modules. The Consensus layer will actually be made up of two different modules -- an API module and an
implementation module, though unless the distinction is important, this document will usually refer to just "the
Consensus Module". The API module will define an `Interface` corresponding to the dotted-line box in the Consensus layer
blue box. The Execution implementation module will have a compile-time dependency on the Consensus layer's API module,
and a runtime dependency on the Consensus layer's implementation module.

Each submodule will likewise be defined by a pair of JPMS modules -- an API module and an implementation module. By
separating the API and implementation modules, we make it possible to supply multiple implementation modules (which is
useful for various testing or future maintenance tasks), and we also support circular dependencies between modules.

### Initialization of the Consensus Module

When Execution starts, it will (at the appropriate time in its startup routine) create an instance of Consensus, and
`initialize` it with appropriate arguments, which will be defined in detail in further documents. Critically,
Consensus **does not maintain state**. Execution is wholly responsible for the definition and management of state. To
start Consensus from a particular moment in time, Execution will need to initialize it with some information such as the
witnesses of the round it wants to start from. It is by using this `initialize` method that Execution is able to create
a Consensus instance that starts from genesis, or from a particular round.

Likewise, if a node needs to reconnect, Execution will `destroy` the existing Consensus, and create a complete new one,
and `initialize` it appropriately with information from the starting round, after having downloaded and initializing
itself with the correct round. Reconnect therefore is the responsibility of Execution. Consensus does not have to
consider reconnect at all.

### Gossip

The Gossip module is responsible for gossiping messages between peers. The actual gossip implementation is not described
here, except to say, that it will be possible to define and implement both event-aware and event-agnostic gossip
implementations either to a fully connected network or where the set of peers is a subset of the whole. Nor does this
document dictate whether raw TCP, UDP, HTTP2, gRPC, or other network protocols are used. This will be left to the design
documents for Gossip.

![Gossip](gossip-system.png)

Gossip is the only part of Consensus that communicates over the network with gossip peers. When Gossip is initialized,
it is supplied a "roster". This roster contains the full set of nodes participating in gossip, along with their metadata
such as RSA signing keys, IP addresses, and so forth. The Gossip module decides which peers to gossip with (using
whatever algorithm it chooses).

#### Events

Gossip is event-oriented, meaning that it is given events to gossip, and emits events it receives through gossip. An
implementation of Gossip could be based on a lower-level implementation based on bytes, but at the module level, it
works in terms of events.

When the Gossip module receives events through gossip, it *may* choose to perform some deduplication before sending
them to Event Intake, but it is not required to do so.

Honest nodes will **only** send valid events in *topological order* to peers. A peer may still receive events out of
order, because different events may arrive from different peers at different times. To support topological ordering,
events received by Gossip are not retained by Gossip, they are simply passed directly to Event Intake, which among
verification tasks, also orders events in topological order. The events are then recorded durably with the
Pre-Consensus Recording module, when then sends these events to the Gossip module to be sent out. This loop is
intentional, with the assumption that the additional latency in this loop will be relatively short (on the order of
microseconds).

#### Peer Discipline

If a peer is misbehaving, the Gossip module will notify the Bad Node module that one of its peers is misbehaving. For
example, if a peer is not responding to requests, even after repeated attempts to reconnect with it, it may be "bad".
Or if the peer is sending events that exceed an acceptable rate, or exceed an acceptable side, then it is "bad". Or if
the events it sends cannot be parsed, then it is "bad". The Gossip module design may define arbitrary additional 

If the Bad Node module decides that the peer should be penalized, then it will instruct the Gossip module to "shun" that
peer. "Shunning" is a unilateral behavior that one node can take towards another, where it terminates the connection and
refuses to work further with that node. If the Bad Node module decides to welcome a peer back into the fold, it can
instruct the Gossip module to "welcome" the peer back.

There are many potential inputs to the Bad Node module, and therefore, the decision to shun or welcome a node is
**not** made by the Gossip module, but rather, the Gossip module must let the Bad Node module know of peer
misbehavior, and the Gossip module must be able to shun and welcome peers on demand.

#### Falling Behind

It may be that during gossip, the Gossip module finds that it cannot continue. Perhaps all connections have failed, or
perhaps the node is so far behind that none of its peers are able to supply any events. In this case, the node is unable
to operate, and must reconnect. Gossip will make a call through the Consensus module interface to notify Execution that
gossip is "down". Execution will then initiate reconnect.

Fundamentally, Execution is responsible for reconnect, but it cannot differentiate from a quiescent network or a
behind node just by looking at the lack of rounds coming from Consensus. So Consensus **must** tell Execution if it
knows that it cannot proceed in creating rounds because the node itself is bad, vs. a quiescent network which is
able to create rounds (if there were any transactions).

#### Roster Changes

At runtime, it is possible that the roster will change dynamically (as happens with the dynamic address book feature).
Roster changes at the gossip level influences which peers the module will work with. Because different peers may be
at different points in consensus time, it may be that Alice has a newer roster than this node does, or Bob may have an
older roster. Bob still needs to be able to gossip, even if he is behind. So each node will need to be able to honor
connections from all peers in all rosters associated with rounds that have not expired.

As with all other modules using rosters, Gossip must have a deterministic understanding of which roster applies to
which round. It will receive this information from Hashgraph in the form of round metadata.

### Event Intake

The Event Intake System is responsible for receiving events, validating them, and emitting them in *topological order*.

#### Validation

One of the core responsibilities of Event Intake is to validate the events it has received. While this document does
not specify the validation pipeline, it will define some of the primary steps involved in validation, so as to motivate
the purpose of this module. That is, the following description is non-normative, but important for understanding the
context within which this module operates.

Event Intake receives events from gossip, or from the Event Creator module (i.e. a "self-event"). An "event" is actually
a wrapper around the base protobuf event type (`EventCore`) that includes the core event data along with metadata that
is not represented in protobuf or transmitted through gossip. One field populated in the metadata will be the byte[]
that represents the protobuf-serialized form of the `EventCore`. Since gossip read this from the wire, there is no work
to produce this `byte[]`, and since the `byte[]` will be needed for Gossip, we need to keep it anyway. But we will also
hash this `byte[]` to produce the event hash, and store this hash in the metadata.

After hashing, Event Intake will deduplicate events. It has a hash->event map allowing it to cheaply verify whether the
event is a duplicate. If it is, the duplicate is discarded. Otherwise, the event is checked for "syntactic" correctness.
For example, are all required fields populated, etc. While the Gossip system has already checked to ensure the payload
of the event (its transactions) are limited in size and count, Event Intake will also check this as a safety measure in
the event of bugs (if this check fails here, a noisy log statement should be produced, since this should never happen).

If an event is valid, then we finally check the signature. Since validation and deduplication and hashing are
significantly less expensive than signature verification, we wait on signature verification until the other steps are
completed. The operating principle is that we want to fail fast and limit work for further stages in the pipeline.

If an event has a very old birth-round that is expired, it is dropped. If a node sends a large number of expired events,
it may end up being disciplined (the exact rules around this will be defined in subsequent design docs for the
Event Intake module).

#### Self Events

Events are not only given to the event intake system through gossip. Internal events are also fed to the event intake
system. These internal events **may** bypass some steps in the pipeline. For example, internal self-events (those
events created by the node itself) do not need validation. Likewise, when replaying events from the pre-consensus
recording system, those checks are not needed (since they have already been proved valid and are in topological order).

#### Peer Discipline

During the validation process, if an event is invalid, it is rejected, and this information is passed to the Bad Node
module so the offending node may be disciplined. Note that the node to be disciplined will be the node that sent this
bad event to us, not the origin node. This information (which node sent the event) must be captured by Gossip and
passed to Event Intake as part of the event metadata.

#### Topological Ordering

Events are buffered if necessary to ensure that each parent event has been emitted from the Event Intake before any
child events. A simple map (the same used for deduplication) can be used here. Given some event, for each parent, look
up the parent by its hash. If each parent is found in the map, then emit the event. Otherwise, remember the event so
when the missing parent is received, the child may be emitted. The current implementation uses what is known as the
"orphan buffer" for this purpose.

Since Event Intake will also maintain some buffers, it needs to know about the progression of the hashgraph,
so it can evict old events. In this case, the "orphan buffer" holds events until either the parent events have
arrived, or the events expired due to the advancement of the "non-ancient event window" and the event is
dropped from the buffer. This document does not prescribe the existence of the orphan buffer or the method by which
events are sorted and emitted in topological order, but it does describe a method by which old events can be dropped.

#### Emitting Events

When the Event Intake module emits valid, topologically sorted events, it sends them to:
- The Event Creator module, so it may have information on which events are available to be built on top of as
  "other parents"
- The Execution layer as a "pre-handle" event
- The Pre-consensus Recording module, so the event can be made durable prior to being added to the hashgraph or gossiped

The call to each of these systems is "fire and forget". Specifically, there is no guarantee to Execution that it will
definitely see an event via `pre-handle` prior to seeing it in `handle`. Technically, Consensus always calls
`pre-handle` first, but that thread may be parked arbitrarily long by the system and the `handle` thread may actually
execute first. This is extremely unlikely, but must be defended against in the Execution layer.

Writing the event durably before gossiping it is essential for self-events to prevent branching. However, to simplify
the understanding of the system, all events will be made durable before gossiping. All events must also be made
durable before being sent to the Hashgraph.

#### Roster Changes

Since Event Intake must validate events, and since event validation requires knowing the roster (to verify the event
source is in the roster, and the gossip source is in the roster, and the signature of the event creator is correct),
Event Intake must know about changes to the roster. As with Gossip, it is imperative that Event Intake maintain a
history of rosters, so it can support peers that are farther behind in consensus than it is.

### Pre-Consensus Recording Module

The Pre-Consensus Recording module is responsible for recording ordered, valid events which have not yet come to
consensus. This is critical for minimizing data loss if catastrophic network failure occurs. This module received
simplification by finding methods that durably persist events fast enough that it can be used inline. That is, each
event is persisted before it is gossiped, and before it is added to the Hashgraph. The delay introduced by this module
**must** be minimized to keep gossip latency minimal and increase the event/sec throughput of the system as a whole.

This system is further simplified by defining its storage as a cyclic buffer. For example, it may have a large file on
disk, and maintain a "head" and "tail" pointer into that file. Then, as new events need to be persisted, it simply
writes them in at the "tail" position and moves forward. As it reaches the end of the file, it loops back to the start.
In this way, it is able to have a fixed buffer, large enough for disaster recovery, but without requiring any feedback
loops from Execution to indicate when data may be purged. Typically, this would be configured to be some value several
multiples in size larger than the state saving timeframe and maximum event/sec rate, so as to provide a solid guarantee
of data availability.

### Public API

There are no changes to the public API as a result of this design.

---

## Implementation and Delivery Plan

How should the proposal be implemented? Is there a necessary order to implementation? What are the stages or phases
needed for the delivery of capabilities? What configuration flags will be used to manage deployment of capability? 
