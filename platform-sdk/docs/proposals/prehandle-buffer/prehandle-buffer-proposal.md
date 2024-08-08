# Pre-handle buffer design proposal

---

## Summary

Proposal to replace the custom thread synchronization solution for pre-handle with a component that would buffer rounds
until pre-handle is complete.

| Metadata           | Entities          | 
|--------------------|-------------------|
| Designers          | Lazar             |
| Functional Impacts | Platform internal |
| Related Proposals  | none              |
| HIPS               | none              |

---

## Purpose and Context

Currently, there is a CountdownLatch inside each event which ensure that pre-handle is always done before the event is
handled in consensus order. The main advantage of this solution is that it is a very simple implementation. But there 
are more drawbacks which are:

- It can lead to performance inefficiencies because it has the possibility of blocking the event handling thread.
- It mixes business logic with concurrency control, which is against the established architecture.
- It creates a possibility for a unit test that to hang indefinitely.
- It is not compatible with TURTLE tests for the same reason, it could make a test hang indefinitely.

The proposal is to replace this mechanism with a component that would buffer rounds until pre-handle is complete.

### Requirements

The proposed solution must meet the following requirements:

- It must ensure that pre-handle is always done before the event is handled in consensus order.
- It must not block the event handling thread.
- It must not mix business logic with concurrency control.

### Design Decisions

The proposed solution will be implemented as a new component that will be responsible for buffering rounds until 
pre-handle is complete. The buffering component mechanism was chosen as a solution because:

- There is a precedent for this kind of solution in the platform, the `RoundDurabilityBuffer` component.
- It is a fairly simple solution that has a high likelihood of being implemented in the short term.
- It will address the problems with the current solution.
- The wiring metrics will automatically give us insight into any pre-handle issues.

#### Alternatives Considered

A single alternative solution was considered, using the `ForkJoinTask.join()` mechanism.
Advantages of this solution are:

- There would not be a need for a new component.
- Implementing this mechanism in the wiring framework would be useful for other use cases.

Disadvantages of this solution are:

- It seems tricky to implement, pre-handle tasks are created at an earlier stage in the pipeline than handle tasks. We 
  would somehow need to map multiple pre-handle tasks to a single handle task that is created at a later stage.
- There are a lot of unknowns about this approach, which means the outcome and ETA would be uncertain.
- It might require a significant change to the wiring framework.

---

## Changes

### Architecture and/or Components

#### New component

A new component, `PreHandleBuffer`, would be added to the system. This component would be responsible for buffering 
rounds until pre-handle is complete. The `PreHandleBuffer` would track which events have been pre-handled based on their
sequence number. When a consensus round is sent to the buffer, it needs to check every event in the round to see if it
has been pre-handled. If all events in the round have been pre-handled, the round can be sent to the next component in 
the pipeline.

The buffer will have 2 inputs: pre-handled events and consensus rounds. The buffer's output will be consensus rounds
whose events have been pre-handled. The only challenge with implementing this component is keeping track of which events
have been pre-handled, which could arrive in any order, as well as not storing data after it is no longer needed.

The pre-handled events that arrive in the buffer can come in any order. They will each have a unique sequence number.
These numbers are assigned sequentially once events have been sorted in topological order. After this, multiple events 
can be sent to pre-handle concurrently. Once pre-handle is done, they are sent to the buffer. The sequence numbers the
buffer will receive will not have vastly different values, which will be the key to removing data from the buffer once
it is no longer needed. The event sequencer also gives us a guarantee that there will be no gaps in the sequence numbers.

The proposed data structure to store this information is an array that acts as a circular buffer. The size of the array 
can grow as needed. The array will use its index as a key, and will store boolean values indicating whether the event 
with given sequence number has been pre-handled.

##### Example

Suppose our array looks like this:

| Array index  | 0   | 1   | 2   | 3   | 4   | 5   |
|--------------|-----|-----|-----|-----|-----|-----|
| Sequence     | 103 | 104 | 105 | 100 | 101 | 102 |
| Pre-handled? | N   | Y   | Y   | N   | Y   | Y   |

Apart from this array, we will also keep two additional values:

- `minSequence` - the minimum sequence number that has not been pre-handled.
- `startIndex` - the array index of the minimum sequence number.

| Min sequence | Start index |
|--------------|-------------|
| 100          | 3           |

Checking if an element has been pre-handled is:
- if its smaller than `minSequence`, it has been pre-handled.
- if its larger than `minSequence + arraySize`, it has not been pre-handled.
- if its between `minSequence` and `minSequence + arraySize`, we can check the array.

Updating the data structure:
Suppose our input is that the event with the sequence number 100. We would first update the array:

| Array index  | 0   | 1   | 2   | 3   | 4   | 5   |
|--------------|-----|-----|-----|-----|-----|-----|
| Sequence     | 103 | 104 | 105 | 100 | 101 | 102 |
| Pre-handled? | N   | Y   | Y   | Y   | Y   | Y   |

Our next step would be to check if we can shift the data. We start from `startIndex` and iterate until we find an event
which has not been handled. Then we update the values appropriately:

| Array index  | 0   | 1   | 2   | 3   | 4   | 5   |
|--------------|-----|-----|-----|-----|-----|-----|
| Sequence     | 103 | 104 | 105 | 106 | 107 | 108 |
| Pre-handled? | N   | Y   | Y   | N   | N   | N   |

| Min sequence | Start index |
|--------------|-------------|
| 103          | 0           |

#### Wiring changes

The following changes would be made to the wiring:
- A new component, `PreHandleBuffer`, would be added to the wiring.
- The `PcesSequencer` would be renamed to `EventSequencer` since it is no longer only used for PCES events.
- The `TransactionPrehandler` would be wired to the `EventSequencer` instead of the `OrphanBuffer` because we need to
  ensure that events have a sequence number before they are pre-handled. This is necessary because the `PreHandleBuffer`
  will track events based on their sequence number.
- The `TransactionPrehandler` would output pre-handled events to the `PreHandleBuffer`. This will notify the buffer 
  which events have been pre-handled.
- The `PreHandleBuffer` would be wired in-between the `ConsensusEngine` and the `RoundDurabilityBuffer`. It will buffer
  rounds until pre-handle is complete.

### Metrics

Are there new metrics? Are the computation of existing metrics changing? Are there expected observable metric impacts
that change how someone should relate to the metric?

Remove this section if not applicable.

### Performance

No performance impacts are expected. There could be a potential for a performance improvement in the case where events 
are not pre-handled by the time they reach consensus. In practice, this is not a common occurrence. The only situation 
where this is expected might be a low latency network.

---

## Test Plan

### Unit Tests

The following unit tests need to be created for the `PreHandleBuffer`:

- basic update and query tests
- array capacity expanding test
- tests with gaps in sequence numbers and no gaps

---

## Implementation and Delivery Plan

The implementation could be broken down into the following steps:

- Create the `PreHandleBuffer` component with unit tests.
- Rename and rewire the `PcesSequencer`.
- Introduce the `PreHandleBuffer` into the wiring.
- Remove the current pre-handle synchronization mechanism.
