// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.blocks.impl;

import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_ACCOUNTS;
import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_CLPR_ENDPOINT_MANIFEST;
import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_PLATFORM_STATE;
import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_THROTTLE_USAGE_SNAPSHOTS;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.blockstream.BlockStreamInfo;
import com.hedera.hapi.node.state.clpr.ClprEndpointManifest;
import com.hedera.hapi.node.state.entity.EntityCounts;
import com.hedera.hapi.node.state.throttles.ThrottleUsageSnapshot;
import com.hedera.hapi.node.state.throttles.ThrottleUsageSnapshots;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.platform.state.ConsensusSnapshot;
import com.hedera.hapi.platform.state.PlatformState;
import com.hedera.node.app.hapi.utils.blocks.StateChangeDeltas;
import com.hedera.node.app.metrics.StoreMetricsServiceImpl;
import com.hedera.node.app.service.entityid.impl.schemas.V0590EntityIdSchema;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hiero.consensus.fakes.noop.NoOpMetrics;
import org.junit.jupiter.api.Test;

class StateChangeDeltaEmissionTest {

    @Test
    void emittedJournalUsesTheActualStoredInstanceWhenStorageProvidesAnEqualCopy() throws Exception {
        final var root =
                Account.newBuilder().memo("m".repeat(100)).tinybarBalance(1).build();
        final var first = root.copyBuilder().tinybarBalance(2).build();
        final var storedFirst = Account.PROTOBUF.parse(Account.PROTOBUF.toBytes(first));
        final var second = storedFirst.copyBuilder().tinybarBalance(3).build();
        final var storedSecond = Account.PROTOBUF.parse(Account.PROTOBUF.toBytes(second));
        final var listener = new ImmediateStateChangeListener();
        listener.beginBlock(true);
        listener.mapUpdateChange(ACCOUNTS, KEY, root, first, storedFirst);
        listener.encodeForStream(item(listener.getKvStateChanges()));
        listener.resetKvStateChanges(null);
        listener.mapUpdateChange(ACCOUNTS, KEY, storedFirst, second, storedSecond);
        try (var deltas =
                org.mockito.Mockito.mockStatic(StateChangeDeltas.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            final var encoded = listener.encodeForStream(item(listener.getKvStateChanges()));
            assertThat(encoded.stateChangesOrThrow()
                            .stateChanges()
                            .getFirst()
                            .mapUpdateOrThrow()
                            .partial())
                    .isTrue();
            deltas.verify(() -> StateChangeDeltas.encodeMap(
                    org.mockito.ArgumentMatchers.eq(ACCOUNTS),
                    org.mockito.ArgumentMatchers.same(storedFirst),
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.eq(64)));
        }
        assertThat(storedFirst).isEqualTo(first).isNotSameAs(first);
    }

    private static final int ACCOUNTS = STATE_ID_ACCOUNTS.protoOrdinal();
    private static final AccountID KEY = AccountID.newBuilder().accountNum(123).build();
    private static final Account A = Account.newBuilder()
            .accountId(KEY)
            .key(com.hedera.hapi.node.base.Key.newBuilder().ed25519(Bytes.wrap("k".repeat(100))))
            .tinybarBalance(10)
            .memo("a")
            .build();
    private static final Account B = A.copyBuilder().tinybarBalance(20).build();
    private static final Account C = B.copyBuilder().memo("c").build();

    @Test
    void independentlyParsedNodesProduceIdenticalBlockItemBytes() throws Exception {
        final var cached = new ImmediateStateChangeListener();
        final var parsed = new ImmediateStateChangeListener();
        cached.beginBlock(true);
        parsed.beginBlock(true);
        cached.mapUpdateChange(ACCOUNTS, KEY, A, C);
        parsed.mapUpdateChange(
                ACCOUNTS,
                AccountID.PROTOBUF.parse(AccountID.PROTOBUF.toBytes(KEY)),
                Account.PROTOBUF.parse(Account.PROTOBUF.toBytes(A)),
                Account.PROTOBUF.parse(Account.PROTOBUF.toBytes(C)));
        assertThat(BlockItem.PROTOBUF.toBytes(cached.encodeForStream(item(cached.getKvStateChanges()))))
                .isEqualTo(BlockItem.PROTOBUF.toBytes(parsed.encodeForStream(item(parsed.getKvStateChanges()))));
    }

    @Test
    void encodesAgainstEmittedOrderIncludingStaleLineageAndNoOp() {
        final var listener = new ImmediateStateChangeListener();
        listener.beginBlock(true);
        listener.reset(key -> true);
        listener.mapUpdateChange(ACCOUNTS, KEY, A, B);
        listener.mapUpdateChange(ACCOUNTS, KEY, B, C);
        final var commits = List.copyOf(listener.getKvStateChanges());
        // Simulate builder reordering. The consumer sees C, then B; the second delta must clear/revert memo.
        final var encoded = listener.encodeForStream(item(List.of(commits.get(1), commits.get(0))));
        final var updates = encoded.stateChangesOrThrow().stateChanges();
        Object replay = A;
        for (final var update : updates) {
            assertThat(update.mapUpdateOrThrow().partial()).isTrue();
            assertThat(update.mapUpdateOrThrow().identical()).isTrue();
            replay = StateChangeDeltas.applyMap(ACCOUNTS, replay, update.mapUpdateOrThrow());
        }
        assertThat(replay).isEqualTo(B);
        listener.reset(null);
        listener.mapUpdateChange(ACCOUNTS, KEY, B, B);
        final var noop = listener.encodeForStream(item(listener.getKvStateChanges()))
                .stateChangesOrThrow()
                .stateChanges()
                .getFirst()
                .mapUpdateOrThrow();
        assertThat(noop.partial()).isTrue();
        assertThat(noop.identical()).isFalse();
        assertThat(noop.valueOrThrow().accountValueOrThrow()).isEqualTo(Account.DEFAULT);
        assertThat(StateChangeDeltas.applyMap(ACCOUNTS, B, noop)).isEqualTo(B);
    }

    @Test
    void disabledModePreservesBytesAndDoesNotRequestOldValues() {
        final var listener = new ImmediateStateChangeListener();
        listener.beginBlock(false);
        listener.mapUpdateChange(ACCOUNTS, KEY, A, B);
        final var full = item(listener.getKvStateChanges());
        assertThat(listener.requiresPreviousValue()).isFalse();
        assertThat(listener.encodeForStream(full)).isSameAs(full);
    }

    @Test
    void deleteRecreateAndBlockResetUseCorrectBaseline() {
        final var listener = new ImmediateStateChangeListener();
        listener.beginBlock(true);
        listener.mapUpdateChange(ACCOUNTS, KEY, A, B);
        listener.encodeForStream(item(listener.getKvStateChanges()));
        listener.reset(null);
        listener.mapDeleteChange(ACCOUNTS, KEY);
        listener.mapUpdateChange(ACCOUNTS, KEY, null, C);
        final var recreated = listener.encodeForStream(item(listener.getKvStateChanges()))
                .stateChangesOrThrow()
                .stateChanges()
                .get(1)
                .mapUpdateOrThrow();
        assertThat(recreated.partial()).isFalse();
        listener.beginBlock(true);
        listener.reset(null);
        listener.mapUpdateChange(ACCOUNTS, KEY, C, A);
        final var nextBlock = listener.encodeForStream(item(listener.getKvStateChanges()))
                .stateChangesOrThrow()
                .stateChanges()
                .getFirst()
                .mapUpdateOrThrow();
        assertThat(StateChangeDeltas.applyMap(ACCOUNTS, C, nextBlock)).isEqualTo(A);
    }

    @Test
    void coalescesSingletonsAgainstFirstPriorAndResetsAtFlush() {
        final var listener = boundaryListener();
        listener.beginBlock(true);
        final int stateId = STATE_ID_THROTTLE_USAGE_SNAPSHOTS.protoOrdinal();
        final var a = ThrottleUsageSnapshots.newBuilder()
                .tpsThrottles(java.util.Collections.nCopies(32, new ThrottleUsageSnapshot(1, null)))
                .gasThrottle(ThrottleUsageSnapshot.newBuilder().used(10).build())
                .build();
        final var b = a.copyBuilder()
                .gasThrottle(ThrottleUsageSnapshot.newBuilder().used(20).build())
                .build();
        final var c = b.copyBuilder()
                .gasThrottle(ThrottleUsageSnapshot.newBuilder().used(30).build())
                .build();
        listener.singletonUpdateChange(stateId, a, b);
        listener.singletonUpdateChange(stateId, b, c);
        final var update = listener.allStateChanges().getFirst().singletonUpdateOrThrow();
        assertThat(update.partial()).isTrue();
        assertThat(StateChangeDeltas.applySingleton(stateId, a, update)).isEqualTo(c);
        listener.reset();
        listener.singletonUpdateChange(stateId, c, a);
        listener.singletonUpdateChange(stateId, a, c);
        final var noop = listener.allStateChanges().getFirst().singletonUpdateOrThrow();
        assertThat(noop.partial()).isTrue();
        assertThat(noop.throttleUsageSnapshotsValueOrThrow()).isEqualTo(ThrottleUsageSnapshots.DEFAULT);
        assertThat(StateChangeDeltas.applySingleton(stateId, c, noop)).isEqualTo(c);
    }

    @Test
    void recoverySingletonAndMigrationStayFull() {
        final var listener = boundaryListener();
        final int stateId = STATE_ID_THROTTLE_USAGE_SNAPSHOTS.protoOrdinal();
        listener.singletonUpdateChange(stateId, ThrottleUsageSnapshots.DEFAULT, ThrottleUsageSnapshots.DEFAULT);
        assertThat(listener.allStateChanges()
                        .getFirst()
                        .singletonUpdateOrThrow()
                        .partial())
                .isFalse();
        listener.reset();
        listener.beginBlock(true);
        final int blockInfo =
                com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_BLOCK_STREAM_INFO.protoOrdinal();
        final var prior = BlockStreamInfo.newBuilder()
                .trailingBlockHashes(Bytes.wrap("h".repeat(200)))
                .build();
        listener.singletonUpdateChange(
                blockInfo, prior, prior.copyBuilder().blockNumber(123).build());
        assertThat(listener.allStateChanges(true)
                        .getFirst()
                        .singletonUpdateOrThrow()
                        .partial())
                .isTrue();
        assertThat(listener.allStateChanges(false)
                        .getFirst()
                        .singletonUpdateOrThrow()
                        .partial())
                .isFalse();
    }

    private static final int ENTITY_COUNTS = V0590EntityIdSchema.ENTITY_COUNTS_STATE_ID;
    private static final EntityCounts COUNTS = EntityCounts.newBuilder()
            .numAccounts(1_000)
            .numSchedules(10)
            .numFiles(1_000_000)
            .numTokens(1_000_000)
            .numTopics(1_000_000)
            .build();

    @Test
    void singletonBaselineFollowsMidBlockEmissions() {
        final var listener = boundaryListener();
        listener.beginBlock(true, 0);
        final var scheduled = COUNTS.copyBuilder().numSchedules(11).build();
        final var purged =
                scheduled.copyBuilder().numSchedules(10).numAccounts(1_001).build();
        // Emitted mid-block without a reset, as HandleWorkflow.doStreamingChangesInternal does
        listener.singletonUpdateChange(ENTITY_COUNTS, COUNTS, scheduled);
        final var reader = replaySingletons(COUNTS, listener.allStateChanges());
        assertThat(reader).isEqualTo(scheduled);
        listener.singletonUpdateChange(ENTITY_COUNTS, scheduled, purged);
        final var atClose = listener.allStateChanges();
        assertThat(atClose.getFirst().singletonUpdateOrThrow().partial()).isTrue();
        // num_schedules is back at its block-start value, but readers hold 11
        assertThat(replaySingletons(reader, atClose)).isEqualTo(purged);
    }

    @Test
    void discardedPendingSingletonUpdateIsCarriedByTheNextEmission() {
        final var listener = boundaryListener();
        listener.beginBlock(true, 0);
        final var moreFiles = COUNTS.copyBuilder().numFiles(1_000_001).build();
        final var moreAccounts = moreFiles.copyBuilder().numAccounts(1_001).build();
        listener.singletonUpdateChange(ENTITY_COUNTS, COUNTS, moreFiles);
        // A reset before any emission discards this update; the baseline must survive it
        listener.reset();
        listener.singletonUpdateChange(ENTITY_COUNTS, moreFiles, moreAccounts);
        assertThat(replaySingletons(COUNTS, listener.allStateChanges())).isEqualTo(moreAccounts);
    }

    @Test
    void singletonPendingWhenBlockBeginsOrRecreatedStaysFull() {
        final var listener = boundaryListener();
        listener.beginBlock(true, 0);
        final var first = COUNTS.copyBuilder().numAccounts(1_001).build();
        final var second = first.copyBuilder().numAccounts(1_002).build();
        final var third = second.copyBuilder().numAccounts(1_003).build();
        listener.singletonUpdateChange(ENTITY_COUNTS, COUNTS, first);
        listener.allStateChanges();
        listener.reset();
        // Committed between blocks, as PlatformState is before each round; a restarted node has no baseline
        listener.singletonUpdateChange(ENTITY_COUNTS, first, second);
        listener.beginBlock(true, 0);
        listener.singletonUpdateChange(ENTITY_COUNTS, second, third);
        final var pending = listener.allStateChanges().getFirst().singletonUpdateOrThrow();
        assertThat(pending.partial()).isFalse();
        assertThat(StateChangeDeltas.applySingleton(ENTITY_COUNTS, null, pending))
                .isEqualTo(third);
        listener.reset();

        listener.singletonDeleteChange(ENTITY_COUNTS);
        listener.singletonUpdateChange(ENTITY_COUNTS, null, first);
        assertThat(listener.allStateChanges()
                        .getFirst()
                        .singletonUpdateOrThrow()
                        .partial())
                .isFalse();
    }

    private static final int PLATFORM_STATE = STATE_ID_PLATFORM_STATE.protoOrdinal();
    private static final int MANIFEST = STATE_ID_CLPR_ENDPOINT_MANIFEST.protoOrdinal();
    private static final ClprEndpointManifest NEXT_MANIFEST =
            ClprEndpointManifest.newBuilder().version(2).build();

    @Test
    void captureReturnsOnlyItsOwnUpdatesAndKeepsEveryUpdatePending() {
        final var listener = boundaryListener();
        listener.beginBlock(false);
        listener.singletonUpdateChange(
                ENTITY_COUNTS, COUNTS, COUNTS.copyBuilder().numAccounts(1_001).build());
        listener.startCapture();
        listener.singletonUpdateChange(MANIFEST, ClprEndpointManifest.DEFAULT, NEXT_MANIFEST);
        final var captured = listener.endCapture();
        assertThat(captured).extracting(StateChange::stateId).containsExactly(MANIFEST);
        assertThat(captured.getFirst().singletonUpdateOrThrow().clprEndpointManifestValueOrThrow())
                .isEqualTo(NEXT_MANIFEST);
        assertThat(listener.allStateChanges())
                .extracting(StateChange::stateId)
                .containsExactly(ENTITY_COUNTS, MANIFEST);
    }

    @Test
    void midBlockCaptureLosesNoSingletonUpdate() {
        final var listener = boundaryListener();
        final Map<Integer, Object> reader = new HashMap<>(Map.of(
                PLATFORM_STATE, platformStateAt(1), ENTITY_COUNTS, COUNTS, MANIFEST, ClprEndpointManifest.DEFAULT));
        // The platform commits PlatformState before each round, so it is pending when the block begins
        listener.singletonUpdateChange(PLATFORM_STATE, platformStateAt(1), platformStateAt(2));
        listener.beginBlock(true, 0);
        final var moreAccounts = COUNTS.copyBuilder().numAccounts(1_001).build();
        listener.singletonUpdateChange(ENTITY_COUNTS, COUNTS, moreAccounts);
        // The CLPR reconcile externalizes just its own updates mid-block, as HandleWorkflow does
        listener.startCapture();
        listener.singletonUpdateChange(MANIFEST, ClprEndpointManifest.DEFAULT, NEXT_MANIFEST);
        final var midBlock = listener.endCapture();
        assertThat(midBlock).extracting(StateChange::stateId).containsExactly(MANIFEST);
        replay(reader, midBlock);
        // A later round of the same block
        listener.singletonUpdateChange(PLATFORM_STATE, platformStateAt(2), platformStateAt(3));
        final var evenMoreAccounts =
                moreAccounts.copyBuilder().numAccounts(1_002).build();
        listener.singletonUpdateChange(ENTITY_COUNTS, moreAccounts, evenMoreAccounts);
        final var atClose = listener.allStateChanges();
        listener.reset();
        replay(reader, atClose);

        assertThat(reader)
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        PLATFORM_STATE, platformStateAt(3), ENTITY_COUNTS, evenMoreAccounts, MANIFEST, NEXT_MANIFEST));
        // Pending when the block began, so still full for nodes that restarted at the block boundary
        assertThat(atClose.stream()
                        .filter(change -> change.stateId() == PLATFORM_STATE)
                        .map(change -> change.singletonUpdateOrThrow().partial()))
                .containsExactly(false);
    }

    @Test
    void pendingSingletonUpdatedDuringCaptureIsEmittedWithItsLatestValue() {
        final var listener = boundaryListener();
        listener.beginBlock(true, 0);
        final var first = COUNTS.copyBuilder().numAccounts(1_001).build();
        final var second = first.copyBuilder().numFiles(1_000_001).build();
        listener.singletonUpdateChange(ENTITY_COUNTS, COUNTS, first);
        listener.startCapture();
        listener.singletonUpdateChange(ENTITY_COUNTS, first, second);
        final var reader = replaySingletons(COUNTS, listener.endCapture());
        assertThat(reader).isEqualTo(second);
        assertThat(replaySingletons(reader, listener.allStateChanges())).isEqualTo(second);
    }

    @Test
    void nodeRestartedAtTheBlockBoundaryEmitsTheSameBytes() {
        final var running = boundaryListener();
        // This node streamed the previous block, so it holds baselines and has deltas enabled
        running.beginBlock(true, 0);
        running.singletonUpdateChange(
                ENTITY_COUNTS, COUNTS, COUNTS.copyBuilder().numFiles(1_000_001).build());
        running.singletonUpdateChange(PLATFORM_STATE, platformStateAt(1), platformStateAt(2));
        running.allStateChanges();
        running.reset();
        final var fromRunning = clprBlockFrom(running);
        final var fromRestarted = clprBlockFrom(boundaryListener());
        assertThat(fromRunning)
                .anyMatch(change -> change.singletonUpdateOrThrow().partial());
        assertThat(BlockItem.PROTOBUF.toBytes(item(fromRestarted)))
                .isEqualTo(BlockItem.PROTOBUF.toBytes(item(fromRunning)));
    }

    @Test
    void aCaptureLeftOpenByAFailedActionIsReplacedByTheNextOne() {
        final var listener = boundaryListener();
        listener.beginBlock(false);
        listener.startCapture();
        listener.singletonUpdateChange(
                ENTITY_COUNTS, COUNTS, COUNTS.copyBuilder().numAccounts(1_001).build());
        // The action threw before endCapture(), so the next streaming action starts a fresh capture
        listener.startCapture();
        listener.singletonUpdateChange(MANIFEST, ClprEndpointManifest.DEFAULT, NEXT_MANIFEST);
        assertThat(listener.endCapture()).extracting(StateChange::stateId).containsExactly(MANIFEST);
        assertThat(listener.endCapture()).isEmpty();
        assertThat(listener.allStateChanges())
                .extracting(StateChange::stateId)
                .containsExactly(ENTITY_COUNTS, MANIFEST);
    }

    // One CLPR-enabled block as HandleWorkflow drives it, from the same committed state
    private static List<StateChange> clprBlockFrom(final BoundaryStateChangeListener listener) {
        final var counts = COUNTS.copyBuilder().numFiles(1_000_001).build();
        listener.singletonUpdateChange(PLATFORM_STATE, platformStateAt(2), platformStateAt(3));
        listener.beginBlock(true, 0);
        listener.singletonUpdateChange(
                ENTITY_COUNTS, counts, counts.copyBuilder().numAccounts(1_001).build());
        listener.startCapture();
        listener.singletonUpdateChange(MANIFEST, ClprEndpointManifest.DEFAULT, NEXT_MANIFEST);
        final var emitted = new ArrayList<>(listener.endCapture());
        listener.singletonUpdateChange(PLATFORM_STATE, platformStateAt(3), platformStateAt(4));
        emitted.addAll(listener.allStateChanges());
        listener.reset();
        return emitted;
    }

    private static PlatformState platformStateAt(final long round) {
        return PlatformState.newBuilder()
                .roundsNonAncient(26)
                .consensusSnapshot(ConsensusSnapshot.newBuilder()
                        .round(round)
                        .nextConsensusNumber(round * 1_000)
                        .build())
                .legacyRunningEventHash(Bytes.wrap("running-hash-" + round))
                .build();
    }

    private static void replay(final Map<Integer, Object> reader, final List<StateChange> changes) {
        for (final var change : changes) {
            final var stateId = change.stateId();
            reader.put(
                    stateId,
                    StateChangeDeltas.applySingleton(stateId, reader.get(stateId), change.singletonUpdateOrThrow()));
        }
    }

    private static Object replaySingletons(final Object prior, final List<StateChange> changes) {
        Object value = prior;
        for (final var change : changes) {
            value = StateChangeDeltas.applySingleton(change.stateId(), value, change.singletonUpdateOrThrow());
        }
        return value;
    }

    private static BoundaryStateChangeListener boundaryListener() {
        return new BoundaryStateChangeListener(
                new StoreMetricsServiceImpl(new NoOpMetrics()), HederaTestConfigBuilder::createConfig);
    }

    private static BlockItem item(final List<StateChange> changes) {
        return BlockItem.newBuilder()
                .stateChanges(StateChanges.newBuilder().stateChanges(changes))
                .build();
    }
}
