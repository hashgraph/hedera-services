# Application State API

## Summary

Refactor the Merkle tree so that it can be fully represented by the State API objects.
Create a module that will provide a simple and easy-to-use API for the Merkle tree.

## Purpose and Context

### Why?

The Merkle tree is a rather complex data structure, and to make interaction easier, we need to hide this complexity behind the API.
Another reason to have this API is to provide additional flexibility in changing the implementation details of the Merkle tree.
As long as they are hidden, we are free to change them. This API will be critically important for the Block Node to 
interact with the state without knowing the details of the Merkle tree.

Note that this API is already implemented as a part of the Service Modularization project, but these classes will be moved
to a separate module as a part of this proposal.

### Context

There are two issues that need to be addressed:

* We need to have a public API for the Merkle tree that is simple and easy to use, residing in a separate module with a minimal set of dependencies.
* The Merkle tree has to be fully expressed in terms of State API. Currently, `PlatformState` stands out and needs to be refactored.

#### Singleton

A singleton is a node with 2 children:

- string label for State Name
- protobuf message for child data

In Java code it's represented by the following classes:
```java
public interface ReadableSingletonState<T> {
  @NonNull
  String getStateKey();
  
  T get();
  
  boolean isRead();
}

public interface WritableSingletonState<T> extends ReadableSingletonState<T> {
  void put(@Nullable T value);

  boolean isModified();
}
```

## Requirements

- `PlatformState` should be refactored to a singleton object as defined by the State API.
- there should be a new module - `swirlds-state-api` - to host a set of interfaces, records, and abstract classes that represent the Hashgraph state.
  This module should have a minimal set of dependencies. The Block Node should not have a compile-time dependency
  on any other modules but this one to interact with the state.

## Changes

### Public API

State API classes and interfaces will migrate to a designated module. The classes themselves will not change. `PlatformState` class will become a part of the public API.

Usage example:
```java
private PlatformState findPlatformState(HederaState state) {
  final ReadableStates states = state.getReadableStates(PlatformState.NAME);   
  final ReadableSingletonState<PlatformState> platformState = states.getSingleton(PlatformState.PLATFORM_STATE_KEY);
  return platformState.get();
}
```

## Components and Architecture

### Platform State

Currently `PlatformState` is a special case node of the merkle tree. That is, it doesn't conform to State API. 
As a part of the simplification effort **it should be refactored to a singleton state**.

Protobuf definition:

```
/**
 * The current state of platform consensus.<br/>
 * This message stores the current consensus data for the platform
 * in network state.
 *
 * The platform state SHALL represent the latest round's consensus.<br/>
 * This data SHALL be used to ensure consistency and provide critical data for
 * restart and reconnect.
 */
message PlatformState {
    /**
     * A consensus round.<br/>
     * The round represented by this state.
     * <p>
     * This message SHALL represent the network state after handling of all
     * transactions reaching consensus in all previous rounds.<br/>
     * The first state (genesis state) SHALL have a round of 0.<br/>
     * The first round to handle transactions SHALL be round `1`.
     */
    uint64 round = 1;

    /**
     * A consensus timestamp for this state.
     * <p>
     * This SHALL be the consensus timestamp of the first transaction in
     * the current round.
     */
    proto.Timestamp consensus_timestamp = 2;

    /**
     * A version describing the current version of application software.
     * <p>
     * This SHALL be the software version that created this state.
     */
    SoftwareVersion creation_software_version = 3;

    /**
     * A number of non-ancient rounds.
     * <p>
     * This SHALL be the count of rounds considered non-ancient.
     */
    uint32 rounds_non_ancient = 4;

    /**
     * A snapshot of the consensus state at the end of the round.
     * <p>
     * This SHALL be used for restart/reconnect.
     */
    ConsensusSnapshot consensus_snapshot = 5;

    /**
     * A timestamp for the next scheduled time when a freeze will start.
     * <p>
     * If a freeze is not scheduled, this SHALL NOT be set.<br/>
     * If a freeze is currently scheduled, this MUST be set, and MUST
     * match the timestamp requested for that freeze.
     */
    proto.Timestamp freeze_time = 6;

    /**
     * A timestamp for the last time a freeze was performed.<br/>
     * If not set, there has never been a freeze.
     */
    proto.Timestamp last_frozen_time = 7;

    /**
     * A consensus node software version.<br/>
     * The software version that enabled birth round mode.
     * <p>
     * This SHALL be unset if birth round migration has not yet happened.<br/>
     * If birth round migration is complete, this SHALL be the _first_ software
     * version that enabled birth round mode.
     */
    SoftwareVersion first_version_in_birth_round_mode = 8;

    /**
     * A consensus round.<br/>
     * The last round before the birth round mode was enabled.
     * <p>
     * This SHALL be `MAX_UNSIGNED` if birth round mode has not yet been enabled.
     */
    uint64 last_round_before_birth_round_mode = 9;

    // Fields below are to be deprecated in the foreseeable future.

    /**
     * A running event hash.<br/>
     * This is computed by the consensus event stream.
     * <p>
     * This will be _deprecated_ once the consensus event stream is retired.
     */
     bytes legacy_running_event_hash = 10000 [deprecated = true];

    /**
     * A consensus generation.<br/>
     * The lowest judge generation before birth round mode was enabled.
     * <p>
     * This SHALL be `MAX_UNSIGNED` if birth round mode has not yet been enabled.
     */
    uint64 lowest_judge_generation_before_birth_round_mode = 10001 [deprecated = true];
}


/**
 * A consensus node software version.
 *
 * This message records version information for configuration, the Hedera
 * API, and the "Services" subsystems.
 */
message SoftwareVersion {
    /**
     * A single numeric version.<br/>
     * This is the current configuration version read on node startup.
     */
    uint32 config_version = 1;

    /**
     * A semantic version entry.<br/>
     * This is the version of the HAPI module (Hedera API).
     */
    proto.SemanticVersion hapi_version = 2;

    /**
     * A semantic version entry.<br/>
     * This is the version of the services module.
     */
    proto.SemanticVersion services_version = 3;
}


/**
 * A consensus snapshot.<br/>
 * This is a snapshot of the consensus state for a particular round.
 *
 * This message SHALL record consensus data necessary for restart
 * and reconnect.
 */
message ConsensusSnapshot {
    /**
     * A consensus round.<br/>
     * The round number of this snapshot.
     */
    uint64 round = 1;

    /**
     * A list of SHA-384 hash values.<br/>
     * The hashes of all judges for this round.
     * <p>
     * This list SHALL be ordered by creator ID.<br/>
     * This list MUST be deterministically ordered.
     */
    repeated bytes judge_hashes = 2;

    /**
     * A list of minimum judge information entries.<br/>
     * These are "minimum ancient" entries for non-ancient rounds.
     */
    repeated MinimumJudgeInfo minimum_judge_info_list = 3;

    /**
     * A single consensus number.<br/>
     * The consensus order of the next event to reach consensus.
     */
    uint64 next_consensus_number = 4;

    /**
     * A "consensus" timestamp.<br/>
     * The consensus timestamp of this snapshot.
     * <p>
     * This SHALL be a consensus value and MAY NOT correspond to an actual
     * "wall clock" timestamp.<br/>
     * Consensus Timestamps SHALL always increase.
     */
    proto.Timestamp consensus_timestamp = 5;
}

/**
 * Minimum ancient threshold information for round judges.<br/>
 * This message records the minimum ancient threshold agreed by all judges in
 * a round.
 */
message MinimumJudgeInfo {
    /**
     * A consensus round.<br/>
     * The round this judge information applies to.
     */
    uint64 round = 1;

    /**
     * Minimum ancient threshold for all judges in the round.
     * <p>
     * This SHALL reflect the relevant minimum threshold, whether
     * generation-based or birth-round-based.
     */
    uint64 minimum_judge_ancient_threshold = 2;
}

```

## Test plan

Migration testing:

- we need to make sure that the data of the current platform state migrates properly to a platform state represented by a singleton node.
- we need to test that `PlatformState` can be properly migrated to protobuf format

## Implementation and delivery plan

- Move State API implementation classes from `swirlds-platform-core` to `swirlds-state-api`.
- Refactor `PlatformState` into a singleton. Migrate the data.
- Move `MerlkeHederaState` to `swirlds-state-api`
