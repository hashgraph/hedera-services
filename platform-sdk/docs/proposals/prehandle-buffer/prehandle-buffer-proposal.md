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

Describe the requirements and acceptance criteria that the design proposal must satisfy.

### Design Decisions

Describe the decisions made and the reasons why.

#### Alternatives Considered

Describe any alternatives considered and why they were not chosen.

If possible, provide a table illustrating the options, evaluation criteria, and scores that factored into the decision.


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
