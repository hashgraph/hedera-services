# Shadowgraph synchronizer

The goal of the shadowgraph synchronizer is to compare graphs with a remote node, and update them so both sides have the
same events in the graph. The protocol used to achieve this is described [here](syncing/sync-protocol.md). This document
describes how it is implemented.

### Overview

A sync starts by the gossip thread invoking the synchronizer.

1. It first makes a call to the Shadowgraph to get the oldest non-expired generation, and to reserve that generation so
   that it does not expire it while the sync is ongoing.
2. It then gets the non-ancient and max-round generations from Consensus.
3. During the sync, it executes 3 phases (described [here](syncing/sync-protocol.md)). For each phase, it queries the
   Shadowgraph for relevant information.
4. Then it uses SyncComms to create read/write tasks.
5. That will be executed in parallel by the ParallelExecutor.

In the last phase, as events are read, they are sent to the EventTaskCreator for processing. At the end of the sync, it
will notify the EventTaskCreator that a sync has been completed, it will then decide whether to create an event or not.

### Aborting syncs

A sync can be aborted for the following reasons:

- The connection between the nodes has been broken
- One node initiated the sync, but the other node rejected to sync
- If it is determined that either node has fallen behind. Fallen behind means that one node has expired (removed) events
  from the graph that the other needs in order to build their graph. In this case, there is no point in exchanging
  events since we cannot add events to the graph before adding their ancestors. This is done after the first phase of
  the sync when generations are compared. Each side will compare their generation numbers to the peer's, and abort the
  sync silently. There is no need for extra messages to be sent since both nodes have reached the same conclusion.
