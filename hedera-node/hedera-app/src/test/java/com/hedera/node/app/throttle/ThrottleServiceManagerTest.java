/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.throttle;

import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.records.BlockRecordService.EPOCH;
import static com.hedera.node.app.service.file.impl.FileServiceImpl.BLOBS_KEY;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.fromPbj;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.toPbj;
import static com.hedera.node.app.throttle.CongestionThrottleService.CONGESTION_LEVEL_STARTS_STATE_KEY;
import static com.hedera.node.app.throttle.CongestionThrottleService.THROTTLE_USAGE_SNAPSHOTS_STATE_KEY;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.congestion.CongestionLevelStarts;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.state.throttles.ThrottleUsageSnapshot;
import com.hedera.hapi.node.state.throttles.ThrottleUsageSnapshots;
import com.hedera.hapi.node.transaction.ThrottleDefinitions;
import com.hedera.node.app.fees.congestion.CongestionMultipliers;
import com.hedera.node.app.hapi.utils.throttles.DeterministicThrottle;
import com.hedera.node.app.hapi.utils.throttles.GasLimitDeterministicThrottle;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableSingletonState;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.state.WritableSingletonState;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.util.FileUtilities;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.VersionedConfigImpl;
import com.hedera.node.config.data.FilesConfig;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ThrottleServiceManagerTest {
    private static final Configuration DEFAULT_CONFIG = HederaTestConfigBuilder.createConfig();
    private static final Bytes MOCK_ENCODED_THROTTLE_DEFS = Bytes.wrap("NOPE");
    private static final ThrottleDefinitions MOCK_THROTTLE_DEFS = ThrottleDefinitions.DEFAULT;
    private static final ThrottleUsageSnapshots MOCK_THROTTLE_USAGE_SNAPSHOTS = new ThrottleUsageSnapshots(
            List.of(new ThrottleUsageSnapshot(123L, EPOCH)), new ThrottleUsageSnapshot(456L, EPOCH));
    private static final CongestionLevelStarts MOCK_CONGESTION_LEVEL_STARTS =
            new CongestionLevelStarts(List.of(new Timestamp(1L, 2), EPOCH), List.of(new Timestamp(3L, 4), EPOCH));
    private static final DeterministicThrottle.UsageSnapshot MOCK_USAGE_SNAPSHOT =
            new DeterministicThrottle.UsageSnapshot(123L, Instant.ofEpochSecond(1_234_567L, 890));

    @Mock
    private ConfigProvider configProvider;

    @Mock
    private ThrottleParser throttleParser;

    @Mock
    private ThrottleAccumulator ingestThrottle;

    @Mock
    private ThrottleAccumulator backendThrottle;

    @Mock
    private CongestionMultipliers congestionMultipliers;

    @Mock
    private WritableStates writableStates;

    @Mock
    private WritableSingletonState<ThrottleUsageSnapshots> writableThrottleSnapshots;

    @Mock
    private WritableSingletonState<CongestionLevelStarts> writableLevelStarts;

    @Mock
    private ReadableStates fileReadableStates;

    @Mock
    private ReadableStates throttleReadableStates;

    @Mock
    private HederaState hederaState;

    @Mock
    private ReadableKVState<FileID, File> blobs;

    @Mock
    private ReadableSingletonState<ThrottleUsageSnapshots> throttleUsageSnapshots;

    @Mock
    private ReadableSingletonState<CongestionLevelStarts> congestionLevelStarts;

    @Mock
    private GasLimitDeterministicThrottle gasThrottle;

    @Mock
    private DeterministicThrottle cryptoTransferThrottle;

    private ThrottleServiceManager subject;

    @BeforeEach
    void setUp() {
        subject = new ThrottleServiceManager(
                configProvider, throttleParser, ingestThrottle, backendThrottle, congestionMultipliers);
    }

    @Test
    void initsAsExpected() {
        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(DEFAULT_CONFIG, 1L));
        givenMockThrottleDefs();
        givenReadableThrottleState();
        givenThrottleMocks();

        final var inOrder = inOrder(
                configProvider,
                ingestThrottle,
                backendThrottle,
                congestionMultipliers,
                cryptoTransferThrottle,
                gasThrottle);

        subject.initFrom(hederaState);

        inOrder.verify(ingestThrottle).applyGasConfig();
        inOrder.verify(backendThrottle).applyGasConfig();
        inOrder.verify(ingestThrottle).rebuildFor(MOCK_THROTTLE_DEFS);
        inOrder.verify(backendThrottle).rebuildFor(MOCK_THROTTLE_DEFS);
        inOrder.verify(congestionMultipliers).resetExpectations();
        inOrder.verify(cryptoTransferThrottle)
                .resetUsageTo(fromPbj(
                        MOCK_THROTTLE_USAGE_SNAPSHOTS.tpsThrottlesOrThrow().getFirst()));
        inOrder.verify(gasThrottle).resetUsageTo(fromPbj(MOCK_THROTTLE_USAGE_SNAPSHOTS.gasThrottleOrThrow()));
        inOrder.verify(congestionMultipliers)
                .resetUtilizationScaledThrottleMultiplierStarts(asNullTerminatedInstants(
                        MOCK_CONGESTION_LEVEL_STARTS.genericLevelStartsOrThrow().getFirst()));
        inOrder.verify(congestionMultipliers)
                .resetGasThrottleMultiplierStarts(asNullTerminatedInstants(
                        MOCK_CONGESTION_LEVEL_STARTS.gasLevelStartsOrThrow().getFirst()));
    }

    @Test
    void savesInternalStateAsExpected() {
        givenWritableThrottleState();
        givenThrottleMocks();
        given(gasThrottle.usageSnapshot()).willReturn(MOCK_USAGE_SNAPSHOT);
        given(congestionMultipliers.entityUtilizationCongestionStarts())
                .willReturn(asNullTerminatedInstants(
                        MOCK_CONGESTION_LEVEL_STARTS.genericLevelStartsOrThrow().getFirst()));
        given(congestionMultipliers.gasThrottleMultiplierCongestionStarts())
                .willReturn(asNullTerminatedInstants(
                        MOCK_CONGESTION_LEVEL_STARTS.gasLevelStartsOrThrow().getFirst()));

        subject.saveThrottleSnapshotsAndCongestionLevelStartsTo(hederaState);

        verify(writableThrottleSnapshots)
                .put(new ThrottleUsageSnapshots(List.of(toPbj(MOCK_USAGE_SNAPSHOT)), toPbj(MOCK_USAGE_SNAPSHOT)));
        verify(writableLevelStarts).put(MOCK_CONGESTION_LEVEL_STARTS);
    }

    @Test
    void refreshesThrottleConfigurationAsExpected() {
        final var inOrder = inOrder(ingestThrottle, backendThrottle, congestionMultipliers);
        subject.refreshThrottleConfiguration();
        inOrder.verify(ingestThrottle).applyGasConfig();
        inOrder.verify(backendThrottle).applyGasConfig();
        inOrder.verify(congestionMultipliers).resetExpectations();
    }

    @Test
    void recreatesThrottlesAsExpected() {
        final var inOrder = inOrder(ingestThrottle, backendThrottle, congestionMultipliers);
        given(throttleParser.parse(MOCK_ENCODED_THROTTLE_DEFS))
                .willReturn(new ThrottleParser.ValidatedThrottles(MOCK_THROTTLE_DEFS, SUCCESS));
        subject.recreateThrottles(MOCK_ENCODED_THROTTLE_DEFS);
        inOrder.verify(ingestThrottle).rebuildFor(MOCK_THROTTLE_DEFS);
        inOrder.verify(backendThrottle).rebuildFor(MOCK_THROTTLE_DEFS);
        inOrder.verify(congestionMultipliers).resetExpectations();
    }

    @Test
    void updateAllMetricsAsExpected() {
        subject.updateAllMetrics();
        verify(ingestThrottle).updateAllMetrics();
        verify(backendThrottle).updateAllMetrics();
    }

    private Instant[] asNullTerminatedInstants(Timestamp timestamp) {
        return new Instant[] {Instant.ofEpochSecond(timestamp.seconds(), timestamp.nanos()), null};
    }

    private void givenThrottleMocks() {
        given(backendThrottle.allActiveThrottles()).willReturn(List.of(cryptoTransferThrottle));
        given(cryptoTransferThrottle.usageSnapshot()).willReturn(MOCK_USAGE_SNAPSHOT);
        given(backendThrottle.gasLimitThrottle()).willReturn(gasThrottle);
    }

    private void givenMockThrottleDefs() {
        given(hederaState.getReadableStates(FileService.NAME)).willReturn(fileReadableStates);
        given(fileReadableStates.<FileID, File>get(BLOBS_KEY)).willReturn(blobs);
        given(blobs.get(FileUtilities.createFileID(
                        DEFAULT_CONFIG.getConfigData(FilesConfig.class).throttleDefinitions(), DEFAULT_CONFIG)))
                .willReturn(
                        File.newBuilder().contents(MOCK_ENCODED_THROTTLE_DEFS).build());
        given(throttleParser.parse(MOCK_ENCODED_THROTTLE_DEFS))
                .willReturn(new ThrottleParser.ValidatedThrottles(MOCK_THROTTLE_DEFS, SUCCESS));
    }

    private void givenReadableThrottleState() {
        given(hederaState.getReadableStates(CongestionThrottleService.NAME)).willReturn(throttleReadableStates);
        given(throttleUsageSnapshots.get()).willReturn(MOCK_THROTTLE_USAGE_SNAPSHOTS);
        given(throttleReadableStates.<ThrottleUsageSnapshots>getSingleton(THROTTLE_USAGE_SNAPSHOTS_STATE_KEY))
                .willReturn(throttleUsageSnapshots);
        given(congestionLevelStarts.get()).willReturn(MOCK_CONGESTION_LEVEL_STARTS);
        given(throttleReadableStates.<CongestionLevelStarts>getSingleton(
                        CongestionThrottleService.CONGESTION_LEVEL_STARTS_STATE_KEY))
                .willReturn(congestionLevelStarts);
    }

    private void givenWritableThrottleState() {
        given(hederaState.getWritableStates(CongestionThrottleService.NAME)).willReturn(writableStates);
        given(writableStates.<ThrottleUsageSnapshots>getSingleton(THROTTLE_USAGE_SNAPSHOTS_STATE_KEY))
                .willReturn(writableThrottleSnapshots);
        given(writableStates.<CongestionLevelStarts>getSingleton(CONGESTION_LEVEL_STARTS_STATE_KEY))
                .willReturn(writableLevelStarts);
    }
}
