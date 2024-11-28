## Version 0.1

The first implementation can be summed up as follows:

- Send side
  - send events and event hashes
  - when creating an event, add it to everyone's queue for sending
  - events are sent in topological order
  - if there are any hashes to send, send all hashes first
  - only send events if the queue of hashes is empty
  - before sending an event, check if the peer already knows this event
- Receive side
  - when receiving a hash, remember that this peer knows this event
  - when receiving an event, assume it was received in order and proceed with de-duplicating and validation.
    if the event is valid add its hash to everyone's queue and add the event to everyone's queue

This implementation ended up sending a lot of duplicate events unnecessarily. This created a buildup in the intake
queue and well as a lot of garbage objects that resulted in allocation stalls.

## Version 0.2

This implementation worked similarly to the previous one, except that a wait was added before sending an event.
Before sending and event, check the following rules:

- If it's an event created by self, send it immediately
- If it's an event that the peer knows, discard it and don't send it
- If the peer does not know this event, wait for a maximum of `N` milliseconds before sending it

The C2C was very high with this approach, because each event created has to wait for the neighbors to notify us that
they have its parents, or the time to run out. This is because self events and other events were in the same queue.
Self events had to wait for other events to be discarded or sent.

## Version 0.3

- A separate queue was introduced for self events
- A self event is added to its own queue and sent immediately
- We no longer assume events must be received in order
- When we receive an event, we validate it before looking to see if we have the event's parents
- If the event is valid, we add it to the queue of other events
- The queue of other events has the same wait as before, but it no longer holds back self events

This version is an improvement, but there is still a problem with the wait being in wall clock time:

- If a node is slow in processing events, it will delay sending the hash, so we will flood that node with duplicate
  events, making its situation even worse
- If a node's network gets interrupted for a few seconds, or its bandwidth goes down, we have the same effect, making
  its situation worse

## Version 0.4

The only difference in this version is the delay of other events. The delay is based on generation numbers, not wall
clock time:

- The event descriptor (hash) includes a generation number
- We track what the highest generation number sent by a peer is
- Before sending an event, we check the diff between its generation and the max generation. If the diff is higher than
  `X`, we send that event
- We did not use the round generations sent by the peer because the peer might be missing some events in order to reach
  consensus
