# Green Wire - Event Birth Round as Ancient Threshold

Testing of the Green Wire is broken up into the following phases:

* Component Unit Tests
* Pipeline Unit Tests
* JRS Tests

## Implementation

* Introduce `AncientMode` enumeration to switch between `GENERATION_THRESHOLD` and `BIRTH_ROUND_THRESHOLD`.
* Introduce `AncientThreshold` that changes definition based on `AncientMode`.
  * when in `GENERATION_THRESHOLD` mode, `AncientThreshold` is the minimum generation of the judges of
    round (`pendingConsensusRound` - `roundsNonAncient`).
  * when in `BIRTH_ROUND_THRESHOLD` mode, `AncientThreshold` is the minimum birth round of the judges of
    round (`pendingConsensusRound` - `roundsNonAncient`).
* Introduce `NonAncientEventWindow` (aka `EventWindow`) that contains `AncientThreshold` for both `GENERATION_THRESHOLD`
  and `BIRTH_ROUND_THRESHOLD`.
* Events implement a `getAncientIndicator(AncientMode)` method which returns their generation in `GENERATION_THRESHOLD`
  mode and birth round in `BIRTH_ROUND_THRESHOLD` mode.
* Events are ancient when `Event.getAncientIndicator(AncientMode) < EventWindow.getAncientThreshold(AncientMode)`.
* Consensus:
  * Rename `MinGenInfo` to `MininumJudgeInfo` which contains the minimum generation or birth round of judges depending
    on which mode the platform is in.
  * Each `Round` produced by consensus has an `EventWindow` that contains the `AncientThreshold` for that round.
* Software Migration: (First version to use birth round as ancient threshold)
  * Let R be the last round to come to consensus pre-migration.
  * Let G be the minimum generation of the judges of round R.
  * For all events with an older software version:
    * if the event's generation is less than G, then its birth round is set to `ROUND_FIRST` and it will be ANCIENT.
    * if the event's generation is greater than or equal to G, then its birth round is set to `R` and it will be
      NON-ANCIENT (at the moment of migration).
    * Update the consensus snapshot, for each non-ancient round, set the minimum judge ancient indicator to be R
    * when changing an event's birth round the hash of the event will not be modified.

# New Edge Cases

### Minimum Birth Round of Judges

Initially the `AncientThreshold` was implemented as `pendingConsensusRound - roundsNonAncient` but an edge case is
possible where more than `roundsNonAncient` number of rounds come to consensus all at once causing critical events to
become ancient too soon. The definition of ancient for birth rounds was taken as the minimum birth round of the judges
to fix this.

### Initial EventWindow

The initial `AncientThreshold` in the two modes is different. In `GENERATION_THRESHOLD` mode, the
initial `AncientThreshold` is `EventConstants.FIRST_GENERATION = 0` and in `BIRTH_ROUND_THRESHOLD` mode, the
initial `AncientThreshold` is `ConsensusConstants.ROUND_FIRST = 1`.

### Sequence Data Structures

The sequential data structures need to be initialized with different method accessors for the two modes.
In `GENERATION_THRESHOLD` mode, the data structures are initialized with `Event :: getGeneration()` and
in `BIRTH_ROUND_THRESHOLD` mode, the data structures are initialized with `Event :: getBirthRound()`

# Component Unit Tests

These unit tests are implemented in the PR that modifies the component to handle birth round as ancient.

Component unit tests are expanded to handle both modes, either through parameterized tests or by creating separate unit
tests for each mode.
The Component's unit tests cover the following cases:

* The ability of the component to receive events with birth rounds in addition to generation
* The ability of the component to receive EventWindows containing the AncientThreshold.
* The ability of the component to behave appropriately when an event is determined to be ancient by the event window.
  * The behavior of the component must be the same in both modes, i.e. that it handles ancient events in the same way.

# Intake Unit Tests

Intake unit tests wire multiple components together to test the pipeline for receiving events from gossip all the way
through to consensus.

## TestFixture Modifications

### GraphGenerator

The `GraphGenerator` is modified to have an internal instance of `ConsensusImpl` so it can give events their accurate
birth round.

* Before each event is created, the `pendingConsensusRound` is queried from the internal `ConsensusImpl`
* The created event receives a birth round equal to `pendingConsensusRound`
* The event is added to the internal `ConsensusImpl` which may cause the `pendingConsensusRound` to increment.
* The event is then emitted for use in testing.

### TestIntake

The `TestIntake` class is modified to receive the `AncientMode` as a constructor argument and sets up the internal
pipeline of components in the same ancient mode.

### ConsensusTests

The `ConsensusTests` are modified to receive an additional parameter for the `AncientMode`.

# JRS Tests

## Genesis JRS Test

A new generic JRS test is started from genesis with a feature flag that turns on `BIRTH_ROUND_THRESHOLD` mode for all
components. This is a smoke test and the only condition is that there are no errors generated during the execution for 5
minutes.

## Migration JRS Test

A new migration JRS test is started from a pre-existing state with a feature flag that turns on `BIRTH_ROUND_THRESHOLD`
mode for all components. The only condition is that there are no errors generated during migration and the system starts
as expected.

## Reconnect Test

Test loading of state that has birth round as ancient instead of generation.

## Perf Test

Longevity test, high stress, large number of nodes, with reconnects.
