/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state;

import static com.hedera.node.app.service.mono.state.StateModule.provideStateViews;
import static com.hedera.node.app.spi.config.PropertyNames.BOOTSTRAP_GENESIS_PUBLIC_KEY;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.hedera.node.app.service.mono.config.NetworkInfo;
import com.hedera.node.app.service.mono.context.MutableStateChildren;
import com.hedera.node.app.service.mono.context.properties.PropertySource;
import com.hedera.node.app.service.mono.ledger.ids.SeqNoEntityIdSource;
import com.hedera.node.app.service.mono.state.logic.ProcessLogicModule;
import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import com.hedera.node.app.service.mono.state.migration.MigrationRecordsManager;
import com.hedera.node.app.service.mono.state.migration.RecordingMigrationManager;
import com.hedera.node.app.service.mono.state.submerkle.RecordingSequenceNumber;
import com.hedera.node.app.service.mono.state.submerkle.SequenceNumber;
import com.hedera.node.app.service.mono.store.schedule.ScheduleStore;
import com.hedera.node.app.service.mono.utils.replay.ReplayAssetRecording;
import com.swirlds.common.system.Platform;
import com.swirlds.common.utility.CommonUtils;
import java.nio.charset.Charset;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StateModuleTest {
    @Mock
    private MigrationRecordsManager migrationRecordsManager;

    @Mock
    private MutableStateChildren stateChildren;

    @Mock
    private ScheduleStore scheduleStore;

    @Mock
    private BooleanSupplier isRecordingFacilityMocks;

    @Mock
    private MutableStateChildren workingState;

    @Mock
    private PropertySource properties;

    @Mock
    private NetworkInfo networkInfo;

    @Mock
    private ReplayAssetRecording assetRecording;

    @Mock
    private MerkleNetworkContext networkContext;

    @Test
    void providesRecordingSeqNumbersIfApropos() {
        given(isRecordingFacilityMocks.getAsBoolean()).willReturn(true);
        given(workingState.networkCtx()).willReturn(networkContext);
        given(networkContext.seqNo()).willReturn(new SequenceNumber());
        final var ids =
                StateModule.provideWorkingEntityIdSource(assetRecording, workingState, isRecordingFacilityMocks);
        final var seqNo = ((SeqNoEntityIdSource) ids).getSeqNo().get();
        assertInstanceOf(RecordingSequenceNumber.class, seqNo);
    }

    @Test
    void providesNormalSeqNumbersOtherwise() {
        given(workingState.networkCtx()).willReturn(networkContext);
        given(networkContext.seqNo()).willReturn(new SequenceNumber());
        final var ids =
                StateModule.provideWorkingEntityIdSource(assetRecording, workingState, isRecordingFacilityMocks);
        final var seqNo = ((SeqNoEntityIdSource) ids).getSeqNo().get();
        assertInstanceOf(SequenceNumber.class, seqNo);
    }

    @Test
    void providesDefaultCharset() {
        // expect:
        assertEquals(
                Charset.defaultCharset(), StateModule.provideNativeCharset().get());
    }

    @Test
    void canGetSha384() {
        // expect:
        assertDoesNotThrow(() -> StateModule.provideDigestFactory().forName("SHA-384"));
    }

    @Test
    void notificationEngineAvail() {
        // expect:
        assertDoesNotThrow(() ->
                StateModule.provideNotificationEngine(mock(Platform.class)).get());
    }

    @Test
    void viewUsesWorkingStateChildren() {
        final var viewFactory = provideStateViews(scheduleStore, workingState, networkInfo);

        assertDoesNotThrow(viewFactory::get);
    }

    @Test
    void looksUpExpectedKey() {
        final var keyBytes = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes();
        final var hexedKeyBytes = CommonUtils.hex(keyBytes);

        given(properties.getStringProperty(BOOTSTRAP_GENESIS_PUBLIC_KEY)).willReturn(hexedKeyBytes);

        // when:
        final var keySupplier = StateModule.provideSystemFileKey(properties);
        // and:
        final var key = keySupplier.get();

        // then:
        assertArrayEquals(keyBytes, key.getEd25519());
    }

    @Test
    void failsWithClearlyInvalidGenesisKey() {
        final var keyBytes = "aaaaaaaaaaaaaaaa".getBytes();
        final var hexedKeyBytes = CommonUtils.hex(keyBytes);

        given(properties.getStringProperty(BOOTSTRAP_GENESIS_PUBLIC_KEY)).willReturn(hexedKeyBytes);

        final var keySupplier = StateModule.provideSystemFileKey(properties);
        assertThrows(IllegalStateException.class, keySupplier::get);
    }

    @Test
    void usesStandardMigrationManagerIfNotRecordingFacilityMocks() {
        final var manager = StateModule.provideMigrationRecordsManager(
                stateChildren, assetRecording, migrationRecordsManager, isRecordingFacilityMocks);

        assertInstanceOf(MigrationRecordsManager.class, manager);
    }

    @Test
    void usesRecordingMigrationManagerIfRecordingFacilityMocks() {
        given(isRecordingFacilityMocks.getAsBoolean()).willReturn(true);

        final var manager = StateModule.provideMigrationRecordsManager(
                stateChildren, assetRecording, migrationRecordsManager, isRecordingFacilityMocks);

        assertInstanceOf(RecordingMigrationManager.class, manager);
    }
}
