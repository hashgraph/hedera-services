# Requirements

We need all merkle leaves in the state to be serialized in protobuf format. Currently `PlatformState.java` uses
the old platform serialization format.

# Proposal

## Protobuf

Migrate the platform state to the protobuf format detailed in this PR: https://github.com/hashgraph/hedera-protobufs/pull/349

This protobuf only contains non-deprecated fields. Some of the fields in the current platform state are no longer in
use, and these will not be carried forward.

Rosters are notably absent from this protobuf. More on that in the [Rosters](#rosters) section.

## Rosters

When we eventually have TSS, there will be the need for a special purpose map that holds rosters. Currently, we need
exactly two rosters: the "current roster" and the "previous roster".

Since TSS design has not yet been finalized, the plan is to move the rosters into a temporary location.

Create a merkle leaf called `PlatformRosters`. This merkle leaf will contain both rosters needed by the platform. It
will be serialized using the legacy format and will not have a protobuf. When TSS is implemented, this will be
deprecated and replaced with the roster storage mechanism needed for TSS.

## Migration pathway

When a node starts up and loads its state, it will check to see if the platform state is in the old format or in the
new format. If the old format is detected, it will translate the old state into the new format and continue with
that new state.

Once block streams are a thing, such a migration would need need to happen during the handling of the first round
that reaches consensus after the upgrade. Since that isn't the case yet, we can take the easy pathway and do migration
at startup time.

# Test Plan

## Unit Tests

- Create a unit test for the TransactionHandler. Given a particular round, we should verify that the state produced
has a platform state with all of the fields set correctly.
- Create a state in the old format and run it through the migration pathway. Verify that the new state is correct.

## End-to-End Tests

This is one of those things that will explode spectacularly if it is not working properly,
regardless of the test scenario. No new tests are required.
