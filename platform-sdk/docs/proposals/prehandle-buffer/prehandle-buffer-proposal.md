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

Describe any new or modified components or architectural changes. This includes thread management changes, state
changes, disk I/O changes, platform wiring changes, etc. Include diagrams of architecture changes.

Remove this section if not applicable.

### Module Organization and Repositories

Describe any new or modified modules or repositories.

Remove this section if not applicable.

### Core Behaviors

Describe any new or modified behavior. What are the new or modified algorithms and protocols? Include any diagrams that
help explain the behavior.

Remove this section if not applicable.

### Public API

Describe any public API changes or additions. Include stakeholders of the API.

Examples of public API include:

* Anything defined in protobuf
* Any functional API that is available for use outside the module that provides it.
* Anything written or read from disk

Code can be included in the proposal directory, but not committed to the code base.

Remove this section if not applicable.

### Configuration

Describe any new or modified configuration.

Remove this section if not applicable.

### Metrics

Are there new metrics? Are the computation of existing metrics changing? Are there expected observable metric impacts
that change how someone should relate to the metric?

Remove this section if not applicable.

### Performance

Describe any expected performance impacts. This section is mandatory for platform wiring changes.

Remove this section if not applicable.

---

## Test Plan

### Unit Tests

Describe critical test scenarios and any higher level functionality tests that can run at the unit test level.

Examples:

* Subtle edge cases that might be overlooked.
* Use of simulators or frameworks to test complex component interaction.

Remove this section if not applicable.

### Integration Tests

Describe any integration tests needed. Integration tests include migration, reconnect, restart, etc.

Remove this section if not applicable.

### Performance Tests

Describe any performance tests needed. Performance tests include high TPS, specific work loads that stress the system,
JMH benchmarks, or longevity tests.

Remove this section if not applicable.

---

## Implementation and Delivery Plan

How should the proposal be implemented? Is there a necessary order to implementation? What are the stages or phases
needed for the delivery of capabilities? What configuration flags will be used to manage deployment of capability? 
