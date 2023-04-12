package com.hedera.node.app.service.mono.records;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.BDDMockito.given;

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

        final var manager =
                RecordsModule.provideRecordStreamManager(
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

        final var manager =
                RecordsModule.provideRecordStreamManager(
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