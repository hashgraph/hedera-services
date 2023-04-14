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

package com.hedera.node.app.service.mono.records;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.context.properties.NodeLocalProperties;
import com.hedera.node.app.service.mono.stats.MiscRunningAvgs;
import com.hedera.node.app.service.mono.stream.RecordStreamManager;
import com.hedera.node.app.service.mono.stream.RecordStreamType;
import com.hedera.node.app.service.mono.stream.RecordingRecordStreamManager;
import com.hedera.node.app.service.mono.utils.replay.ReplayAssetRecording;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import java.io.File;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecordsModuleTest {
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
    private ReplayAssetRecording assetRecording;

    @Mock
    private BooleanSupplier isRecordingFacilityMocks;

    @Test
    void providesRecordingRecordStreamManagerIfApropos() {
        given(isRecordingFacilityMocks.getAsBoolean()).willReturn(true);
        given(platform.getSelfId()).willReturn(new NodeId(false, 0));
        given(nodeLocalProperties.recordLogDir()).willReturn(tempDir.toString());

        final var manager = RecordsModule.provideRecordStreamManager(
                platform,
                runningAvgs,
                nodeLocalProperties,
                "0.0.3",
                initialHash,
                streamType,
                globalDynamicProperties,
                assetRecording,
                isRecordingFacilityMocks);
        assertInstanceOf(RecordingRecordStreamManager.class, manager);

        manager.close();
    }

    @Test
    void providesNonRecordingRecordStreamManagerIfApropos() {
        given(platform.getSelfId()).willReturn(new NodeId(false, 0));
        given(nodeLocalProperties.recordLogDir()).willReturn(tempDir.toString());

        final var manager = RecordsModule.provideRecordStreamManager(
                platform,
                runningAvgs,
                nodeLocalProperties,
                "0.0.3",
                initialHash,
                streamType,
                globalDynamicProperties,
                assetRecording,
                isRecordingFacilityMocks);
        assertInstanceOf(RecordStreamManager.class, manager);

        manager.close();
    }
}
