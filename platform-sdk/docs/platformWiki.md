# Swirlds Platform Wiki

This document contains information about the Swirlds Platform. It is currently a work in progress.

The platform code is split into three categories: Core, Base, and Data. TODO expand

## Core

This code is maintained by the "Platform Hashgraph" team.

- Components
  - Gossip
    - Sync gossip algorithm
    - Out of order gossip algorithm
  - Hashgraph
  -  State management
    - State snapshots
    - Hashing
    - State Signing
    - ISS Detection
  - Reconnect
  - Transaction Handling
  - BLS
- Event Flow
  - [Pre-consensus event stream](components/preConsensusEventStream.md)
  - Post-consensus event stream
  - Threading Diagram
- State Proofs

## Base

- Configuration
- Metrics
  - Prometheus
- Logging
- Thread Management
- Notification Engine

## Data

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