/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.node.app.service.mono.state.submerkle;

import com.hedera.node.app.service.mono.utils.replay.NewId;
import com.hedera.node.app.service.mono.utils.replay.ReplayAssetRecording;
import edu.umd.cs.findbugs.annotations.NonNull;

public class RecordingSequenceNumber extends SequenceNumber {
    public static final String REPLAY_SEQ_NOS_ASSET = "replay-sequence-numbers.txt";
    private final ReplayAssetRecording assetRecording;
    private final SequenceNumber delegate;

    public RecordingSequenceNumber(
            @NonNull final ReplayAssetRecording assetRecording, @NonNull final SequenceNumber delegate) {
        this.delegate = delegate;
        this.assetRecording = assetRecording;
    }

    @Override
    public long getAndIncrement() {
        final var ans = delegate.getAndIncrement();
        final var next = new NewId();
        next.setNumber(ans);
        assetRecording.appendJsonToAsset(REPLAY_SEQ_NOS_ASSET, next);
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
