package com.hedera.node.app.integration.facilities;

import com.hedera.node.app.service.mono.state.submerkle.RecordingSequenceNumber;
import com.hedera.node.app.service.mono.utils.replay.NewId;
import com.hedera.node.app.service.mono.utils.replay.ReplayAssetRecording;
import edu.umd.cs.findbugs.annotations.NonNull;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.function.LongSupplier;

@Singleton
public class ReplayIds implements LongSupplier {
    private final List<NewId> idsToReplay;

    private int i = 0;

    @Inject
    public ReplayIds(@NonNull final ReplayAssetRecording assetRecording) {
        this.idsToReplay = assetRecording.readJsonLinesFromReplayAsset(
                RecordingSequenceNumber.REPLAY_SEQ_NOS_ASSET, NewId.class);
    }

    @Override
    public long getAsLong() {
        return idsToReplay.get(i++).getNumber();
    }
}
