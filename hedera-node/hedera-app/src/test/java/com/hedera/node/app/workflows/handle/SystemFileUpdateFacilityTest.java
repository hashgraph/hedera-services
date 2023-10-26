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

package com.hedera.node.app.workflows.handle;

import static com.hedera.node.app.service.file.impl.FileServiceImpl.BLOBS_KEY;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.file.FileAppendTransactionBody;
import com.hedera.hapi.node.file.FileUpdateTransactionBody;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.fees.congestion.MonoMultiplierSources;
import com.hedera.node.app.fixtures.state.FakeHederaState;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.spi.fixtures.TransactionFactory;
import com.hedera.node.app.throttle.ThrottleAccumulator;
import com.hedera.node.app.throttle.ThrottleManager;
import com.hedera.node.app.util.FileUtilities;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.config.VersionedConfigImpl;
import com.hedera.node.config.converter.BytesConverter;
import com.hedera.node.config.converter.LongPairConverter;
import com.hedera.node.config.data.FilesConfig;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.test.framework.config.TestConfigBuilder;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mock.Strictness;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SystemFileUpdateFacilityTest implements TransactionFactory {

    private static final Bytes FILE_BYTES = Bytes.wrap("Hello World");
    private static final Instant CONSENSUS_NOW = Instant.parse("2000-01-01T00:00:00Z");

    @Mock(strictness = Strictness.LENIENT)
    private ConfigProviderImpl configProvider;

    private FakeHederaState state;

    private Map<FileID, File> files;

    private SystemFileUpdateFacility subject;

    private ThrottleManager throttleManager;

    @Mock
    private ExchangeRateManager exchangeRateManager;

    @Mock
    private MonoMultiplierSources monoMultiplierSources;

    @Mock
    private ThrottleAccumulator throttleAccumulator;

    @Mock
    private ThrottleAccumulator synchronizedThrottleAccumulator;

    @BeforeEach
    void setUp() {
        files = new HashMap<>();
        state = new FakeHederaState().addService(FileService.NAME, Map.of(BLOBS_KEY, files));

        final var config = new TestConfigBuilder(false)
                .withConverter(new BytesConverter())
                .withConverter(new LongPairConverter())
                .withConfigDataType(FilesConfig.class)
                .withConfigDataType(LedgerConfig.class)
                .getOrCreateConfig();
        when(configProvider.getConfiguration()).thenReturn(new VersionedConfigImpl(config, 1L));

        throttleManager = new ThrottleManager();
        subject = new SystemFileUpdateFacility(
                configProvider,
                throttleManager,
                exchangeRateManager,
                monoMultiplierSources,
                throttleAccumulator,
                synchronizedThrottleAccumulator);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testMethodsWithInvalidArguments() {
        // given
        final var txBody = simpleCryptoTransfer().body();
        final var recordBuilder = new SingleTransactionRecordBuilderImpl(CONSENSUS_NOW);

        // then
        assertThatThrownBy(() -> new SystemFileUpdateFacility(
                        null,
                        throttleManager,
                        exchangeRateManager,
                        monoMultiplierSources,
                        throttleAccumulator,
                        synchronizedThrottleAccumulator))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SystemFileUpdateFacility(
                        configProvider,
                        null,
                        exchangeRateManager,
                        monoMultiplierSources,
                        throttleAccumulator,
                        synchronizedThrottleAccumulator))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SystemFileUpdateFacility(
                        configProvider,
                        throttleManager,
                        null,
                        monoMultiplierSources,
                        throttleAccumulator,
                        synchronizedThrottleAccumulator))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SystemFileUpdateFacility(
                        configProvider,
                        throttleManager,
                        exchangeRateManager,
                        null,
                        throttleAccumulator,
                        synchronizedThrottleAccumulator))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SystemFileUpdateFacility(
                        configProvider,
                        throttleManager,
                        exchangeRateManager,
                        monoMultiplierSources,
                        null,
                        synchronizedThrottleAccumulator))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SystemFileUpdateFacility(
                        configProvider,
                        throttleManager,
                        exchangeRateManager,
                        monoMultiplierSources,
                        throttleAccumulator,
                        null))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> subject.handleTxBody(null, txBody)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> subject.handleTxBody(state, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> subject.handleTxBody(state, txBody)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testCrytpoTransferShouldBeNoOp() {
        // given
        final var txBody = TransactionBody.newBuilder()
                .cryptoTransfer(CryptoTransferTransactionBody.DEFAULT)
                .build();
        final var recordBuilder = new SingleTransactionRecordBuilderImpl(CONSENSUS_NOW);

        // then
        assertThatCode(() -> subject.handleTxBody(state, txBody)).doesNotThrowAnyException();
    }

    @Test
    void testUpdateConfigFile() {
        // given
        final var fileID = FileID.newBuilder().fileNum(121L).build();
        final var txBody = TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder()
                        .accountID(AccountID.newBuilder().accountNum(50L).build())
                        .build())
                .fileUpdate(FileUpdateTransactionBody.newBuilder().fileID(fileID));
        files.put(fileID, File.newBuilder().contents(FILE_BYTES).build());

        // when
        subject.handleTxBody(state, txBody.build());

        // then
        verify(configProvider).update(FILE_BYTES);
    }

    @Test
    void testAppendConfigFile() {
        // given
        final var fileID = FileID.newBuilder().fileNum(121L).build();
        final var txBody = TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder()
                        .accountID(AccountID.newBuilder().accountNum(50L).build())
                        .build())
                .fileAppend(FileAppendTransactionBody.newBuilder().fileID(fileID));
        files.put(fileID, File.newBuilder().contents(FILE_BYTES).build());

        // when
        subject.handleTxBody(state, txBody.build());

        // then
        verify(configProvider).update(FILE_BYTES);
    }

    @Test
    void throttleMangerUpdatedOnFileUpdate() {
        // given
        final var configuration = configProvider.getConfiguration();
        final var config = configuration.getConfigData(FilesConfig.class);

        final var fileNum = config.throttleDefinitions();
        final var fileID = FileID.newBuilder().fileNum(fileNum).build();
        final var txBody = TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder()
                        .accountID(AccountID.newBuilder().accountNum(50L).build())
                        .build())
                .fileAppend(FileAppendTransactionBody.newBuilder().fileID(fileID));
        files.put(fileID, File.newBuilder().contents(FILE_BYTES).build());

        throttleManager = Mockito.mock(ThrottleManager.class);
        subject = new SystemFileUpdateFacility(
                configProvider,
                throttleManager,
                exchangeRateManager,
                monoMultiplierSources,
                throttleAccumulator,
                synchronizedThrottleAccumulator);

        // when
        subject.handleTxBody(state, txBody.build());

        // then
        verify(throttleManager, times(1)).update(FileUtilities.getFileContent(state, fileID));
    }

    @Test
    void exchangeRateManagerUpdatedOnFileUpdate() {
        // given
        final var configuration = configProvider.getConfiguration();
        final var config = configuration.getConfigData(FilesConfig.class);

        final var fileNum = config.exchangeRates();
        final var fileID = FileID.newBuilder().fileNum(fileNum).build();
        final var txBody = TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder()
                        .accountID(AccountID.newBuilder().accountNum(50L).build())
                        .build())
                .fileAppend(FileAppendTransactionBody.newBuilder().fileID(fileID));
        files.put(fileID, File.newBuilder().contents(FILE_BYTES).build());

        // when
        subject.handleTxBody(state, txBody.build());

        // then
        verify(exchangeRateManager, times(1))
                .update(
                        FileUtilities.getFileContent(state, fileID),
                        AccountID.newBuilder().accountNum(50L).build());
    }
}
