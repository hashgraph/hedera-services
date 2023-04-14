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

package com.hedera.node.app.integration.facilities;

import com.hedera.node.app.service.mono.state.submerkle.RecordingSequenceNumber;
import com.hedera.node.app.service.mono.utils.replay.NewId;
import com.hedera.node.app.service.mono.utils.replay.ReplayAssetRecording;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.function.LongSupplier;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ReplayIds implements LongSupplier {
    private final List<NewId> idsToReplay;

    private int i = 0;

    @Inject
    public ReplayIds(@NonNull final ReplayAssetRecording assetRecording) {
        this.idsToReplay =
                assetRecording.readJsonLinesFromReplayAsset(RecordingSequenceNumber.REPLAY_SEQ_NOS_ASSET, NewId.class);
    }

    @Override
    public long getAsLong() {
        return idsToReplay.get(i++).getNumber();
    }
}
