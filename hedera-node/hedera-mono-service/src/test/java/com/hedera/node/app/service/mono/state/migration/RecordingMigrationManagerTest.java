package com.hedera.node.app.service.mono.state.migration;

import com.hedera.node.app.service.mono.context.StateChildren;
import com.hedera.node.app.service.mono.state.submerkle.RecordingSequenceNumber;
import com.hedera.node.app.service.mono.utils.replay.ReplayAssetRecording;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RecordingMigrationManagerTest {
    @Mock
    private StateChildren stateChildren;
    @Mock
    private MigrationManager delegate;
    @Mock
    private ReplayAssetRecording assetRecording;

    private RecordingMigrationManager subject;

    @BeforeEach
    void setUp() {
        subject = new RecordingMigrationManager(delegate, stateChildren, assetRecording);
    }

    @Test
    void removesSystemConsumedEntityIdsAndDumpsInitialAccounts() {
        final var inOrder = Mockito.inOrder(stateChildren, delegate, assetRecording);
        final var now = Instant.ofEpochSecond(1_234_567L);

        subject.publishMigrationRecords(now);

        inOrder.verify(delegate).publishMigrationRecords(now);
        inOrder.verify(assetRecording).removeReplayAsset(RecordingSequenceNumber.REPLAY_SEQ_NOS_ASSET);
    }
}