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
import static com.hedera.node.app.service.mono.stream.RecordingRecordStreamManager.RECORDS_ASSET;
import static org.mockito.BDDMockito.given;

import com.google.protobuf.ByteString;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.context.properties.NodeLocalProperties;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.stats.MiscRunningAvgs;
import com.hedera.node.app.service.mono.utils.replay.ReplayAssetRecording;
import com.hedera.test.utils.SeededPropertySource;
import com.hederahashgraph.api.proto.java.Transaction;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.SplittableRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecordingRecordStreamManagerTest {
    @TempDir
    private File tempDir;

    @Mock
    private Platform platform;

    @Mock
    private MiscRunningAvgs runningAvgs;

    @Mock
    private NodeLocalProperties nodeLocalProperties;

    @Mock
    private Hash initialHash;

    @Mock
    private RecordStreamType streamType;

    @Mock
    private GlobalDynamicProperties globalDynamicProperties;

    @Mock
    private ReplayAssetRecording replayAssetRecording;

    private RecordingRecordStreamManager subject;

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException, IOException {
        given(platform.getSelfId()).willReturn(new NodeId(false, 0));
        given(nodeLocalProperties.recordLogDir()).willReturn(tempDir.toString());

        subject = new RecordingRecordStreamManager(
                platform,
                runningAvgs,
                nodeLocalProperties,
                "0.0.3",
                initialHash,
                streamType,
                globalDynamicProperties,
                replayAssetRecording);
    }

    @Test
    void recordsRecordStreamObject() {
        final var source = new SeededPropertySource(new SplittableRandom(1L));
        final var mockRecord = source.nextRecord();
        mockRecord.getTokens().clear();
        final var mockTxn = Transaction.newBuilder()
                .setSignedTransactionBytes(ByteString.copyFromUtf8("NONSENSE"))
                .build();
        final var mockConsensusTimestamp = Instant.ofEpochSecond(1_234_567L, 890);
        final var rso = new RecordStreamObject(mockRecord, mockTxn, mockConsensusTimestamp);

        subject.setInFreeze(true);
        subject.addRecordStreamObject(rso);

        final var pbjRecord = protoToPbj(mockRecord.asGrpc(), TransactionRecord.class);
        final var encodedRecord = PbjConverter.toB64Encoding(pbjRecord, TransactionRecord.class);
        Mockito.verify(replayAssetRecording).appendPlaintextToAsset(RECORDS_ASSET, encodedRecord);

        subject.close();
    }
}
