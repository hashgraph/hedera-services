// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.blocks.impl;

import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_BLOCKS;
import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_BLOCK_STREAM_INFO;
import static com.hedera.node.app.service.clpr.impl.schemas.V0770ClprSchema.ENDPOINT_MANIFEST_CONSTRUCTION_STATE_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.hapi.node.state.blockstream.BlockStreamInfo;
import com.hedera.hapi.node.state.clpr.ClprEndpointManifestConstruction;
import com.hedera.node.app.hapi.utils.blocks.StateChangeDeltas;
import com.hedera.node.app.metrics.StoreMetricsServiceImpl;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.Set;
import java.util.stream.LongStream;
import org.hiero.consensus.fakes.noop.NoOpMetrics;
import org.junit.jupiter.api.Test;

class SingletonStateChangeEmissionTest {
    private static final int BLOCKS = STATE_ID_BLOCKS.protoOrdinal();
    private static final BlockInfo INITIAL = BlockInfo.newBuilder()
            .lastBlockNumber(10)
            .blockHashes(Bytes.wrap("h".repeat(200)))
            .build();
    private static final BlockInfo UPDATED =
            INITIAL.copyBuilder().lastBlockNumber(11).build();
    private final BoundaryStateChangeListener listener = new BoundaryStateChangeListener(
            new StoreMetricsServiceImpl(new NoOpMetrics()), HederaTestConfigBuilder::createConfig);

    @Test
    void emissionFollowedByRevertedFieldUsesFreshCallbackBaseline() {
        listener.beginBlock(true);
        listener.singletonUpdateChange(BLOCKS, INITIAL, UPDATED);
        final var first = listener.stateChangesFor(Set.of(BLOCKS));
        final var firstUpdate = first.getFirst().singletonUpdateOrThrow();
        assertThat(firstUpdate.partial()).isTrue();
        Object replay = StateChangeDeltas.applySingleton(BLOCKS, INITIAL, firstUpdate);
        listener.stateChangesEmitted(first);
        assertThat(listener.allStateChanges()).isEmpty();

        listener.singletonUpdateChange(BLOCKS, UPDATED, INITIAL);
        final var second = listener.stateChangesFor(Set.of(BLOCKS));
        final var secondUpdate = second.getFirst().singletonUpdateOrThrow();
        assertThat(secondUpdate.partial()).isTrue();
        replay = StateChangeDeltas.applySingleton(BLOCKS, replay, secondUpdate);
        assertThat(replay).isEqualTo(INITIAL);
        listener.stateChangesEmitted(second);
        assertThat(listener.allStateChanges()).isEmpty();
    }

    @Test
    void inspectionDoesNotDrainOrAdvanceTheBaseline() {
        listener.beginBlock(true);
        listener.singletonUpdateChange(BLOCKS, INITIAL, UPDATED);
        final var inspected = listener.allStateChanges();
        assertThat(listener.allStateChanges()).isEqualTo(inspected);
        listener.singletonUpdateChange(BLOCKS, UPDATED, INITIAL);
        final var update = listener.allStateChanges().getFirst().singletonUpdateOrThrow();
        assertThat(update.partial()).isTrue();
        assertThat(update.blockInfoValueOrThrow()).isEqualTo(BlockInfo.DEFAULT);
        assertThat(StateChangeDeltas.applySingleton(BLOCKS, INITIAL, update)).isEqualTo(INITIAL);
    }

    @Test
    void selectiveEmissionPreservesUnrelatedPendingValueAndBaseline() {
        listener.beginBlock(true);
        listener.singletonUpdateChange(BLOCKS, INITIAL, UPDATED);
        final var initialConstruction = ClprEndpointManifestConstruction.newBuilder()
                .constructionId(1)
                .targetNodeIds(LongStream.range(0, 100).boxed().toList())
                .build();
        final var nextConstruction =
                initialConstruction.copyBuilder().graceExtensionsUsed(1).build();
        listener.singletonUpdateChange(ENDPOINT_MANIFEST_CONSTRUCTION_STATE_ID, initialConstruction, nextConstruction);
        final var selected = listener.stateChangesFor(Set.of(ENDPOINT_MANIFEST_CONSTRUCTION_STATE_ID));
        assertThat(selected).hasSize(1);
        listener.stateChangesEmitted(selected);
        assertThat(listener.stateChangesFor(Set.of(ENDPOINT_MANIFEST_CONSTRUCTION_STATE_ID)))
                .isEmpty();
        final var pending = listener.allStateChanges();
        assertThat(pending).hasSize(1);
        assertThat(pending.getFirst().stateId()).isEqualTo(BLOCKS);
        assertThat(StateChangeDeltas.applySingleton(
                        BLOCKS, INITIAL, pending.getFirst().singletonUpdateOrThrow()))
                .isEqualTo(UPDATED);

        final var latest = UPDATED.copyBuilder().migrationRecordsStreamed(true).build();
        listener.singletonUpdateChange(BLOCKS, UPDATED, latest);
        final var finalUpdate = listener.allStateChanges().getFirst().singletonUpdateOrThrow();
        assertThat(finalUpdate.partial()).isTrue();
        assertThat(StateChangeDeltas.applySingleton(BLOCKS, INITIAL, finalUpdate))
                .isEqualTo(latest);
    }

    @Test
    void unrelatedUpdateSurvivesEmptyClprPublicationWithoutALaterWrite() {
        listener.beginBlock(true);
        listener.singletonUpdateChange(BLOCKS, INITIAL, UPDATED);
        final var selected = listener.stateChangesFor(Set.of(ENDPOINT_MANIFEST_CONSTRUCTION_STATE_ID));
        assertThat(selected).isEmpty();
        listener.stateChangesEmitted(selected);
        final var finalChanges = listener.allStateChanges();
        assertThat(finalChanges).hasSize(1);
        assertThat(StateChangeDeltas.applySingleton(
                        BLOCKS, INITIAL, finalChanges.getFirst().singletonUpdateOrThrow()))
                .isEqualTo(UPDATED);
    }

    @Test
    void pendingBeforeBlockStartStaysFullUntilPublished() {
        listener.singletonUpdateChange(BLOCKS, INITIAL, UPDATED);
        listener.beginBlock(true);
        final var latest = UPDATED.copyBuilder().migrationRecordsStreamed(true).build();
        listener.singletonUpdateChange(BLOCKS, UPDATED, latest);
        final var changes = listener.allStateChanges();
        final var update = changes.getFirst().singletonUpdateOrThrow();
        assertThat(update.partial()).isFalse();
        assertThat(StateChangeDeltas.applySingleton(BLOCKS, INITIAL, update)).isEqualTo(latest);
        listener.stateChangesEmitted(changes);

        listener.singletonUpdateChange(BLOCKS, latest, INITIAL);
        final var next = listener.allStateChanges().getFirst().singletonUpdateOrThrow();
        assertThat(next.partial()).isTrue();
        assertThat(StateChangeDeltas.applySingleton(BLOCKS, latest, next)).isEqualTo(INITIAL);
    }

    @Test
    void fullCheckpointDrainsAndEstablishesTheNextCallbackBaseline() {
        listener.beginBlock(true);
        final int stateId = STATE_ID_BLOCK_STREAM_INFO.protoOrdinal();
        final var initial = BlockStreamInfo.newBuilder()
                .blockNumber(10)
                .trailingBlockHashes(Bytes.wrap("h".repeat(200)))
                .build();
        final var next = initial.copyBuilder().blockNumber(11).build();
        listener.singletonUpdateChange(stateId, initial, next);
        assertThat(listener.allStateChanges(true)
                        .getFirst()
                        .singletonUpdateOrThrow()
                        .partial())
                .isTrue();
        final var checkpoint = listener.allStateChanges(false);
        assertThat(checkpoint.getFirst().singletonUpdateOrThrow().partial()).isFalse();
        assertThat(StateChangeDeltas.applySingleton(
                        stateId, initial, checkpoint.getFirst().singletonUpdateOrThrow()))
                .isEqualTo(next);
        listener.stateChangesEmitted(checkpoint);
        assertThat(listener.allStateChanges()).isEmpty();

        listener.singletonUpdateChange(stateId, next, initial);
        final var update = listener.allStateChanges().getFirst().singletonUpdateOrThrow();
        assertThat(StateChangeDeltas.applySingleton(stateId, next, update)).isEqualTo(initial);
    }
}
