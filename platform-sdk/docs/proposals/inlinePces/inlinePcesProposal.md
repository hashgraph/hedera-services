# Goal

It is currently possible for nodes to forget about self events when they restart. If such a forgotten self event is
preserved by another node, then it becomes possible for an honest node to create create branching events.

# Proposal

If we can make the PcesWriter fast enough, then we can make self events durable before we gossip them to our peers.

## PCES file changes

Instead of using traditional files like we currently do, convert the PcesWriter to use memory mapped files. Writes
to these files are very fast. Writes to these files are also durable as long as the OS does not crash (i.e.) data
durability does not require the JVM to remain online).

## Asynchronous writing changes

Currently, PCES writing happens asynchronously. That is, an event is inserted into the PCES writer, and while
the event is in the process of being written, other parts of the system utilize it. When it comes time to handle
transactions in a round, we pause if the keystone event for that round has not yet been made durable on disk.

After this change is made, no part of the system after event intake will utilize an event until it has been made
durable by the PcesWriter. This includes gossip.

## PcesSequencer removal

The PcesSequencer will no longer be necessary and can be removed. There will also be no need for keystone events
within rounds.

## RoundDurabilityBuffer removal

The RoundDurabilityBuffer will no longer be necessary and can be removed.

## Diagram Changes

In this image, several existing components are crossed out. This represents a component being removed. The data will
still flow along the same pathways, but with no component on that wire.

![](inlinePces.png)](inlinePces.png)
