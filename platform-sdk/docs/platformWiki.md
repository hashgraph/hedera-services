# Swirlds Platform Wiki

This document contains information about the Swirlds Platform. It is currently a work in progress.

The platform code is split into three categories:

- Core: gossip, consensus, data flow, and various algorithms
- Base: common utilities and libraries; logging, configuration, metrics, etc.
- Data: merkle data structures for holding the ledger state

## Core

This code is maintained by the "Platform Hashgraph" team.

- Components
    - Gossip
        - Sync gossip algorithm
        - Out of order gossip algorithm
    - Hashgraph
    - State management
    - State snapshots
    - Hashing
    - State Signing
    - ISS Detection
    - Reconnect
    - Transaction Handling
    - BLS
    - Notification Engine
- Event Flow
    - Event Intake
    - Pre-consensus event stream
    - Post-consensus event stream
    - Threading Diagram

## Base

This code is maintained by the "Platform Base" team.

- [Configuration](./base/configuration/configuration.md)
- Metrics
    - Prometheus
- Logging
- Thread Management

## Data

This code is maintained by the "Platform Data" team.

- Merkle APIs
    - Fast Copies
    - Mutability
    - Reference Counting
    - Hashing
    - Serialization
- Data Strucutres
    - VirtualMap
        - MerkleDB
    - MerkleMap
    - FCHashMap
    - FCQueue
- Reconnect

## Process

### Testing
### Pull Requests
### Documentation
#### Markdown Wiki
#### Mindmap