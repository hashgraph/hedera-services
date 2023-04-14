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

package com.hedera.node.app.service.mono.stream;

import static com.hedera.node.app.service.mono.pbj.PbjConverter.protoToPbj;

import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.context.properties.NodeLocalProperties;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.stats.MiscRunningAvgs;
import com.hedera.node.app.service.mono.utils.replay.ReplayAssetRecording;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.system.Platform;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;

public class RecordingRecordStreamManager extends RecordStreamManager {
    public static final String RECORDS_ASSET = "records.txt";
    private final ReplayAssetRecording replayAssetRecording;

    public RecordingRecordStreamManager(
            @NonNull final Platform platform,
            @NonNull final MiscRunningAvgs runningAvgs,
            @NonNull final NodeLocalProperties nodeLocalProperties,
            @NonNull final String accountMemo,
            @NonNull final Hash initialHash,
            @NonNull final RecordStreamType streamType,
            @NonNull final GlobalDynamicProperties globalDynamicProperties,
            @NonNull final ReplayAssetRecording replayAssetRecording)
            throws NoSuchAlgorithmException, IOException {
        super(
                platform,
                runningAvgs,
                nodeLocalProperties,
                accountMemo,
                initialHash,
                streamType,
                globalDynamicProperties);
        this.replayAssetRecording = replayAssetRecording;
    }

    @Override
    public void addRecordStreamObject(@NonNull final RecordStreamObject recordStreamObject) {
        super.addRecordStreamObject(recordStreamObject);
        final var pbjRecord = protoToPbj(recordStreamObject.getTransactionRecord(), TransactionRecord.class);
        final var encodedRecord = PbjConverter.toB64Encoding(pbjRecord, TransactionRecord.class);
        replayAssetRecording.appendPlaintextToAsset(RECORDS_ASSET, encodedRecord);
    }
}
