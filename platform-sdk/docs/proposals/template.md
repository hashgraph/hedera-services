# Summary

Give a 1-3 sentence summary of the capability in the design proposal.

| Interactions       | Entities                            | 
|--------------------|-------------------------------------|
| Last Updated       | 2024-06-07  (Edward)                |
| Designers          | John, Mary, alpha-team              |
| Functional Impacts | Services, DevOps, Mirror Node, etc. |
| Related Proposals  | Proposal-1, Proposal-2              |
| HIPS               | HIP-1, HIP-2,                       |

# Purpose and Context

Why is the design proposal necessary? Describe the background and purpose of the design proposal at a high level.

# Requirements

Describe the requirements and acceptance criteria that the design proposal must satisfy.

# Design Decisions

Describe the high level options available, the trade-offs between them and the decisions made. Mark this section as N/A
if not applicable.

# Changes

## Public API

Describe any public API changes or additions. Include stakeholders of the API. Mark this section as N/A if not
applicable.

Code can be included in the proposal directory, but not submitted to the code base.

## Configuration

Describe any new or modified configuration. Mark this section as N/A if not applicable.

## Core Behaviors

Describe any new or modified behavior. What are the new or modified algorithms and protocols? What is the threading
model being used? Mark this section as N/A if not applicable.

### Metrics

Are there new metrics? Are the computation of existing metrics changing? Are there expected observable metric impacts
that change how someone should relate to the metric? Mark this section as N/A if not applicable.

## Performance

Describe any expected performance impacts. This section is mandatory for platform wiring changes. Mark this section as
N/A if not applicable.

## Modules and Repos

Describe any new or modified modules or repositories. Mark this section as N/A if not applicable.

# Test Plan

## Unit Tests

Describe any unit tests needed. Mark this section as N/A if not applicable.

Unit tests should cover all IO for methods and component wiring, and any end-to-end tests that are possible.

## Integration Tests

Describe any integration tests needed. Mark this section as N/A if not applicable.

Integration tests include migration, reconnect, restart, etc.

## Performance Tests

Describe any performance tests needed, or delete this section if not applicable.

Performance tests include high TPS, specific work loads that stress the system, or longevity tests.

# Implementation and Delivery Plan

How should the proposal be implemented? Is there a necessary order to implementation?
What are the stages or phases needed for the delivery of capabilities?
