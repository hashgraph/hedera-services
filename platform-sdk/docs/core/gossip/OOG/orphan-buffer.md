# Orphan Buffer

In order to allow events to be gossiped out of order, we will need a buffer for "orphan" events for which we do not yet
have all of their non-ancient parents

- A received event will go through 2 phases of validation
  - phase 1: check that it can be parsed correctly and that its signature is correct, and it meets basic correctness
    checks (such as its parents generations are not negative and are not so much higher than the highest known event
    that they could be used as an attack to exhaust memory).
  - phase 2: at the moment that its last non-ancient parent has been received and passed phase 2, then the event is
    considered to have passed phase 2.

The hash of an event is sent to all neighbors as soon as it passes phase 1. It is added to the hashgraph as soon as it
passes phase 2. It can only be sent to neighbors after passing phase 2.

## Implementation

The orphan buffer is an `EventLinker` that stores events for which it cannot find parents. If and when the parents are
found, the event is linked to its parents and provided as output.

Data structures used to implement the orphan buffer:

- `missingParents` maps a parent descriptor (hash and generation) to a set of orphans that need this particular parent
- `newlyLinkedEvents` contains a queue of events that have just passed phase 2. this queue is used to check if any of
  the new events are missing parents for some orphans.
- `eventOutput` a queue of linked events ready to be added to the hashgraph

## Examples

### Out of order events

Consider the following graph:

```
3  4
| /|
2  |
| \|
0  1
```

The events will be added in the following order: 0 2 3 4 1

**Add 0**

Event has no parents, so its considered linked

| Missing parent |   |
|----------------|---|
| Orphans        |   |

| Newly linked | 0 |
|--------------|---|

| Output | 0 |
|--------|---|

**Add 2**

Event 2 is missing event 1

| Missing parent | 1 |
|----------------|---|
| Orphans        | 2 |

| Newly linked |   |
|--------------|---|

| Output |   |
|--------|---|

**Add 3**

Event 3 is missing event 2. Even though we have event 2, since it's an orphan, we don't consider it a valid parent

| Missing parent | 1 | 2 |
|----------------|---|---|
| Orphans        | 2 | 3 |

| Newly linked |   |
|--------------|---|

| Output |   |
|--------|---|

**Add 4**

Event 4 is missing both 1 and 3

| Missing parent |  1   | 2 | 3 |
|----------------|------|---|---|
| Orphans        | 2, 4 | 3 | 4 |

| Newly linked |   |
|--------------|---|

| Output |   |
|--------|---|

**Add 1**

When event 1 is added, it will trigger a series of actions where all events will be linked:

1 is linked

| Missing parent |  1   | 2 | 3 |
|----------------|------|---|---|
| Orphans        | 2, 4 | 3 | 4 |

| Newly linked | 1 |
|--------------|---|

| Output | 1 |
|--------|---|

we check if 1 is a missing parent, so we link 2. even though 1 is a parent of 4, we cannot consider it linked until we
link 3 as well

| Missing parent | 2 | 3 |
|----------------|---|---|
| Orphans        | 3 | 4 |

| Newly linked | 2 |
|--------------|---|

| Output | 1 | 2 |
|--------|---|---|

now that 2 is linked, we can link 3

| Missing parent | 3 |
|----------------|---|
| Orphans        | 4 |

| Newly linked | 3 |
|--------------|---|

| Output | 1 | 2 | 3 |
|--------|---|---|---|

when 3 is linked, it will trigger linking 4

| Missing parent |   |
|----------------|---|
| Orphans        |   |

| Newly linked | 4 |
|--------------|---|

| Output | 1 | 2 | 3 | 4 |
|--------|---|---|---|---|
