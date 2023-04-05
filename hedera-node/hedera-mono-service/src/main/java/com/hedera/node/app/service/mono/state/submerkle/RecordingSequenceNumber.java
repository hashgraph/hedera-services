package com.hedera.node.app.service.mono.state.submerkle;

import com.hedera.node.app.service.mono.utils.replay.NewId;
import com.hedera.node.app.service.mono.utils.replay.ReplayAssetRecording;
import edu.umd.cs.findbugs.annotations.NonNull;

public class RecordingSequenceNumber extends SequenceNumber {
    public static final String REPLAY_SEQ_NOS_ASSET = "replay-sequence-numbers.json";
    private final ReplayAssetRecording assetRecording;
    private final SequenceNumber delegate;

    public RecordingSequenceNumber(
            @NonNull final ReplayAssetRecording assetRecording,
            @NonNull final SequenceNumber delegate) {
        this.delegate = delegate;
        this.assetRecording = assetRecording;
    }

    @Override
    public long getAndIncrement() {
        final var ans = delegate.getAndIncrement();
        final var next = new NewId();
        next.setNumber(ans);
        assetRecording.appendJsonLineToReplayAsset(REPLAY_SEQ_NOS_ASSET, next);
        return ans;
    }

    @Override
    public void decrement() {
        delegate.decrement();
    }

    @Override
    public long current() {
        return delegate.current();
    }
}
