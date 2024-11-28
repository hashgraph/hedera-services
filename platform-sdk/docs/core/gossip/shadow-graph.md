# Shadow Graph

This document describes the role and responsibility of the Shadow Graph.

The Shadow Graph is a data structure that maintains non-expired events and provide queries on the data structure for the
purpose of exchanging of events with other nodes, or gossiping. The Shadow Graph contains shadow events, each of which
wraps a hash graph event and provides pointers to the parent shadow events. As gossip occurs, more shadow events are
added to the Shadow Graph. As consensus advances, shadow events are expired from the Shadow Graph.

## Threads

There are two threads that interact with the Shadow Graph:

* **Gossip Thread** - queries for shadow events and adds received events to the Shadow Graph (1 thread per sync)
* **Intake Thread** - expires events from the Shadow Graph

[![](https://mermaid.ink/img/eyJjb2RlIjoiZmxvd2NoYXJ0IFREXG4gICAgU0dbU2hhZG93IEdyYXBoXVxuICAgIEdUKChHb3NzaXAgVGhyZWFkKSkgLS0-fDEgLSByZXNlcnZlfCBTR1xuICAgIEdUIC0tPnwyIC0gcXVlcnl8IFNHXG4gICAgR1QgLS0-fDMgLSBhZGRFdmVudHwgUVRcbiAgICBRVCgoSW50YWtlIFRocmVhZCkpIC0tPnwxIC0gYWRkRXZlbnR8IFNHXG4gICAgUVQgLS0-fDIgLSBleHBpcmVCZWxvd3wgU0dcblxuIiwibWVybWFpZCI6eyJ0aGVtZSI6ImRlZmF1bHQifSwidXBkYXRlRWRpdG9yIjpmYWxzZSwiYXV0b1N5bmMiOnRydWUsInVwZGF0ZURpYWdyYW0iOmZhbHNlfQ)](https://mermaid-js.github.io/mermaid-live-editor/edit/#eyJjb2RlIjoiZmxvd2NoYXJ0IFREXG4gICAgU0dbU2hhZG93IEdyYXBoXVxuICAgIEdUKChHb3NzaXAgVGhyZWFkKSkgLS0-fDEgLSByZXNlcnZlfCBTR1xuICAgIEdUIC0tPnwyIC0gcXVlcnl8IFNHXG4gICAgR1QgLS0-fDMgLSBhZGRFdmVudHwgUVRcbiAgICBRVCgoSW50YWtlIFRocmVhZCkpIC0tPnwxIC0gYWRkRXZlbnR8IFNHXG4gICAgUVQgLS0-fDIgLSBleHBpcmVCZWxvd3wgU0dcblxuIiwibWVybWFpZCI6IntcbiAgXCJ0aGVtZVwiOiBcImRlZmF1bHRcIlxufSIsInVwZGF0ZUVkaXRvciI6ZmFsc2UsImF1dG9TeW5jIjp0cnVlLCJ1cGRhdGVEaWFncmFtIjpmYWxzZX0)

Coordination between the threads is required because the Queue Thread must be prevented from expiring events while a
Gossip Thread is syncing due to the problems caused like the example below. There is one exception for explicit
synchronization which is explained [below](#findAncestors).

### Problematic Expiry Example

Nodes Alice and Bob begin a gossip session. Alice's minimum non-expired event generation is equal to Bob's maximum event
generation, let's say generation 100. Bob has not yet fallen behind Alice, but he is close. The two nodes continue to
phase 2 and exchange booleans. Meanwhile, Alice's Queue Thread expires generation 100 and 101. Bob's maximum event
generation is still 100, but now Alice's minimum non-expired event generation is 102. Bob has now fallen behind Alice
but neither will detect it, so Alice will send her entire hashgraph to Bob who will not able to insert any of the
events.

## Event Expiry Coordination

In order to coordinate the expiration of events with gossiping, the Shadow Graph maintains three variables:

* `oldestGeneration` - the oldest generation that is not yet expired
* `expireBelow` - the generation for which all older generations should be expired, when possible
* `reservationList` - the list of all generations that were at one time reserved and are not yet expired, and their
  current number of reservations

### Gossip Thread

At the beginning of each sync, the gossip thread reserves the `expireBelow` generation. If there is already a
reservation for that generation in the `reservationList`, increment its number of reservations. If there is no existing
reservation in the list for the `expireBelow` generation, add a new reservation to the end of the list and set its
number of reservations to one. The value of `expireBelow` only ever increases, so the order of reservations in the list
will always be ascending.

At the end of each sync, the gossip thread releases its reservation by decrementing the number of reservations for the
specified generation.

Generations are not expired until all its reservations have been released, guaranteeing that events are not expired from
the hash graph while a sync is using those events.

### Queue Thread

When a generation should be expired, the queue thread informs the SGM. The SGM updates the value of `expireBelow` which
increases the generation which gossip threads can reserve.

The `expireBelow()` method performs expiry of events in generations that can and should be expired. Generations that
*should* be expired are generations less than `expireBelow`. Generations that *can* be expired are those that are less
than the lowest reserved generation.

## <a name="findAncestors"></a>Synchronization Exception

The only public method in Shadow Graph that does not need to be synchronized is `findAncestors`. It is important that
this method not be synchronized for high performance. `findAncestors` traverses the shadow graph downward through
ancestors, accessing each shadow event's self parent and other parent (unless it fails the provided predicate). It is
safe for it to not be synchronized because:

1. No data is modified (prevents `ConcurrentModificationException`)
2. Additions to the top of the shadow graph do not affect the searching of ancestors
3. Parent shadow event references are accessed once each and are checked for `null` (prevents `NullPointerException`)
4. It is always invoked with a predicate that rejects events that are below the reserved generation (prevents traversal
   below the reserved generation into events that could be expired

It is also worth noting that `findAncestors` is called very shortly after a memory gate call to `getTips()` which is
synchronized. Before executing a synchronized method, the thread reads all variables from shared memory, so it gets the
latest changes to the shadow graph from other threads shortly before operating on it in `findAncestors()`. The current
implementation does not rely on this memory gate. This explanation is included for reference should changes be made to
the implementation in the future.
