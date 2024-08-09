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

### Public API

There are no changes to the public API as a result of this design.

---

## Implementation and Delivery Plan

How should the proposal be implemented? Is there a necessary order to implementation? What are the stages or phases
needed for the delivery of capabilities? What configuration flags will be used to manage deployment of capability? 
