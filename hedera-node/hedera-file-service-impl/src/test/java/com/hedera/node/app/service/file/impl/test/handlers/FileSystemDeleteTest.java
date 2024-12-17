/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.file.impl.test.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.ENTITY_NOT_ALLOWED_TO_DELETE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.UNAUTHORIZED;
import static com.hedera.node.app.spi.fixtures.Assertions.assertThrowsPreCheck;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.file.SystemDeleteTransactionBody;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.hapi.utils.fee.FileFeeBuilder;
import com.hedera.node.app.service.file.ReadableFileStore;
import com.hedera.node.app.service.file.impl.ReadableFileStoreImpl;
import com.hedera.node.app.service.file.impl.WritableFileStore;
import com.hedera.node.app.service.file.impl.handlers.FileSystemDeleteHandler;
import com.hedera.node.app.service.file.impl.test.FileTestBase;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.fees.FeeCalculator;
import com.hedera.node.app.spi.fees.FeeCalculatorFactory;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.store.ReadableStoreFactory;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.prehandle.PreHandleContextImpl;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileSystemDeleteTest extends FileTestBase {

    @Mock
    private ReadableAccountStore accountStore;

    @Mock(strictness = LENIENT)
    private ReadableFileStoreImpl mockStore;

    @Mock
    private FileSystemDeleteHandler subject;

    @Mock(strictness = LENIENT)
    private PreHandleContext preHandleContext;

    @Mock
    private Instant instant;

    @Mock(strictness = Mock.Strictness.LENIENT)
    protected TransactionDispatcher mockDispatcher;

    @Mock(strictness = Mock.Strictness.LENIENT)
    protected ReadableStoreFactory mockStoreFactory;

    @Mock(strictness = Mock.Strictness.LENIENT)
    protected Account payerAccount;

    @Mock
    private FileFeeBuilder usageEstimator;

    @Mock
    private StoreMetricsService storeMetricsService;

    protected Configuration testConfig;

    @BeforeEach
    void setUp() {
        mockStore = mock(ReadableFileStoreImpl.class);
        subject = new FileSystemDeleteHandler(usageEstimator);

        writableFileState = writableFileStateWithOneKey();
        given(writableStates.<FileID, File>get(FILES)).willReturn(writableFileState);
        testConfig = HederaTestConfigBuilder.createConfig();
        writableStore = new WritableFileStore(writableStates, testConfig, storeMetricsService);
        lenient().when(preHandleContext.configuration()).thenReturn(testConfig);
        lenient().when(handleContext.configuration()).thenReturn(testConfig);
        when(mockStoreFactory.getStore(ReadableFileStore.class)).thenReturn(mockStore);
        when(mockStoreFactory.getStore(ReadableAccountStore.class)).thenReturn(accountStore);
    }

    @Test
    @DisplayName("pureChecks throws exception when file id is null")
    public void testPureChecksThrowsExceptionWhenFileIdIsNull() {
        SystemDeleteTransactionBody transactionBody = mock(SystemDeleteTransactionBody.class);
        TransactionBody transaction = mock(TransactionBody.class);
        given(handleContext.body()).willReturn(transaction);
        given(transaction.systemDeleteOrThrow()).willReturn(transactionBody);
        given(transactionBody.fileID()).willReturn(null);

        assertThatThrownBy(() -> subject.pureChecks(handleContext.body())).isInstanceOf(PreCheckException.class);
    }

    @Test
    @DisplayName("pureChecks does not throw exception when file id is not null")
    public void testPureChecksDoesNotThrowExceptionWhenFileIdIsNotNull() {
        given(handleContext.body()).willReturn(newFileDeleteTxn());

        assertThatCode(() -> subject.pureChecks(handleContext.body())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("calculateFees method invocations")
    public void testCalculateFeesInvocations() {
        FeeContext feeContext = mock(FeeContext.class);
        FeeCalculatorFactory feeCalculatorFactory = mock(FeeCalculatorFactory.class);
        FeeCalculator feeCalculator = mock(FeeCalculator.class);
        when(feeContext.feeCalculatorFactory()).thenReturn(feeCalculatorFactory);
        when(feeCalculatorFactory.feeCalculator(SubType.DEFAULT)).thenReturn(feeCalculator);

        subject.calculateFees(feeContext);

        InOrder inOrder = inOrder(feeContext, feeCalculatorFactory, feeCalculator);
        inOrder.verify(feeContext).body();
        inOrder.verify(feeCalculatorFactory).feeCalculator(SubType.DEFAULT);
        inOrder.verify(feeCalculator).legacyCalculate(any());
    }

    @Test
    @DisplayName("File not found returns error")
    void fileIdNotFound() throws PreCheckException {
        // given:
        mockPayerLookup();
        given(mockStore.getFileMetadata(notNull())).willReturn(null);
        final var context = new PreHandleContextImpl(mockStoreFactory, newFileDeleteTxn(), testConfig, mockDispatcher);

        // when:
        assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_FILE_ID);
    }

    @Test
    @DisplayName("Fails handle if file doesn't exist")
    void fileDoesntExist() {
        given(handleContext.body()).willReturn(newFileDeleteTxn());

        writableFileState = emptyWritableFileState();
        given(writableStates.<FileID, File>get(FILES)).willReturn(writableFileState);
        writableStore = new WritableFileStore(writableStates, testConfig, storeMetricsService);
        given(storeFactory.writableStore(WritableFileStore.class)).willReturn(writableStore);

        HandleException thrown = (HandleException) catchThrowable(() -> subject.handle(handleContext));
        assertThat(thrown.getStatus()).isEqualTo(INVALID_FILE_ID);
    }

    @Test
    @DisplayName("Fails handle if the file is a system file")
    void fileIsSystemFile() {
        given(handleContext.body()).willReturn(newSystemDeleteTxn());

        final var existingFile = writableStore.get(fileSystemFileId);
        assertThat(existingFile.isPresent()).isTrue();
        assertThat(existingFile.get().deleted()).isFalse();
        given(storeFactory.writableStore(WritableFileStore.class)).willReturn(writableStore);

        HandleException thrown = (HandleException) catchThrowable(() -> subject.handle(handleContext));
        assertThat(ENTITY_NOT_ALLOWED_TO_DELETE).isEqualTo(thrown.getStatus());
        assertThat(existingFile.get().deleted()).isFalse();
    }

    @Test
    @DisplayName("Fails handle if keys doesn't exist on file system to be deleted")
    void keysDoesntExist() {
        given(handleContext.body()).willReturn(newFileDeleteTxn());
        file = new File(fileId, expirationTime, null, Bytes.wrap(contents), memo, false, 0L);

        writableFileState = writableFileStateWithOneKey();
        given(writableStates.<FileID, File>get(FILES)).willReturn(writableFileState);
        writableStore = new WritableFileStore(writableStates, testConfig, storeMetricsService);
        given(storeFactory.writableStore(WritableFileStore.class)).willReturn(writableStore);

        HandleException thrown = (HandleException) catchThrowable(() -> subject.handle(handleContext));
        assertThat(thrown.getStatus()).isEqualTo(UNAUTHORIZED);
    }

    @Test
    @DisplayName("Handle works as expected and file system deleted when time is expired(less than epoch second)")
    void handleWorksAsExpectedWhenExpirationTimeIsExpired() {
        given(handleContext.body()).willReturn(newFileDeleteTxn());

        final var existingFile = writableStore.get(fileId);
        assertThat(existingFile.isPresent()).isTrue();
        assertThat(existingFile.get().deleted()).isFalse();
        given(storeFactory.writableStore(WritableFileStore.class)).willReturn(writableStore);

        lenient().when(handleContext.consensusNow()).thenReturn(instant);
        lenient().when(instant.getEpochSecond()).thenReturn(existingFile.get().expirationSecond() + 100);
        subject.handle(handleContext);

        final var changedFile = writableStore.get(fileId);

        assertThat(changedFile).isEqualTo(Optional.empty());
    }

    @Test
    @DisplayName("Handle works as expected and the system file marked as deleted")
    void handleWorksAsExpectedWhenExpirationTimeIsNotExpired() {
        given(handleContext.body()).willReturn(newFileDeleteTxn());

        final var existingFile = writableStore.get(fileId);
        assertThat(existingFile.isPresent()).isTrue();
        assertThat(existingFile.get().deleted()).isFalse();
        given(storeFactory.writableStore(WritableFileStore.class)).willReturn(writableStore);

        lenient().when(handleContext.consensusNow()).thenReturn(instant);
        lenient().when(instant.getEpochSecond()).thenReturn(existingFile.get().expirationSecond() - 100);
        subject.handle(handleContext);

        final var changedFile = writableStore.get(fileId);

        assertThat(changedFile.isPresent()).isTrue();
        assertThat(changedFile.get().deleted()).isTrue();
    }

    private Key mockPayerLookup() throws PreCheckException {
        return FileTestUtils.mockPayerLookup(A_COMPLEX_KEY, payerId, accountStore);
    }

    private TransactionBody newSystemDeleteTxn() {
        final var txnId = TransactionID.newBuilder().accountID(payerId).build();
        final var deleteFileSystemBuilder =
                SystemDeleteTransactionBody.newBuilder().fileID(WELL_KNOWN_SYSTEM_FILE_ID);
        return TransactionBody.newBuilder()
                .transactionID(txnId)
                .systemDelete(deleteFileSystemBuilder.build())
                .build();
    }

    private TransactionBody newFileDeleteTxn() {
        final var txnId = TransactionID.newBuilder().accountID(payerId).build();
        final var deleteFileSystemBuilder =
                SystemDeleteTransactionBody.newBuilder().fileID(WELL_KNOWN_FILE_ID);
        return TransactionBody.newBuilder()
                .transactionID(txnId)
                .systemDelete(deleteFileSystemBuilder.build())
                .build();
    }
}
