package com.hedera.node.app.service.mono.state.migration;

import com.hedera.node.app.service.mono.context.StateChildren;
import com.hedera.node.app.service.mono.utils.replay.ReplayAssetRecording;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.time.Instant;
import java.util.Objects;

public class RecordingMigrationManager implements MigrationManager {
    private final StateChildren stateChildren;
    private final MigrationManager delegate;
    private final ReplayAssetRecording assetRecording;

    public RecordingMigrationManager(
            @NonNull final MigrationManager delegate,
            @NonNull final StateChildren stateChildren,
            @NonNull final ReplayAssetRecording assetRecording) {
        this.delegate = Objects.requireNonNull(delegate);
        this.stateChildren = Objects.requireNonNull(stateChildren);
        this.assetRecording = assetRecording;
    }

    @Override
    public void publishMigrationRecords(@NonNull final Instant now) {

    }
}
