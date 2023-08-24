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

package com.hedera.node.app.service.file.impl.test.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.UNAUTHORIZED;
import static com.hedera.node.app.spi.fixtures.Assertions.assertThrowsPreCheck;
import static com.hedera.test.utils.KeyUtils.A_COMPLEX_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.file.SystemUndeleteTransactionBody;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.file.ReadableFileStore;
import com.hedera.node.app.service.file.impl.ReadableFileStoreImpl;
import com.hedera.node.app.service.file.impl.WritableFileStore;
import com.hedera.node.app.service.file.impl.handlers.FileSystemUndeleteHandler;
import com.hedera.node.app.service.file.impl.test.FileTestBase;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
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
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileSystemUndeleteTest extends FileTestBase {

    @Mock
    private ReadableAccountStore accountStore;

    @Mock
    private ReadableFileStoreImpl mockStore;

    @Mock
    private FileSystemUndeleteHandler subject;

    @Mock(strictness = LENIENT)
    private HandleContext handleContext;

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

    protected Configuration testConfig;

    @BeforeEach
    void setUp() {
        mockStore = mock(ReadableFileStoreImpl.class);
        subject = new FileSystemUndeleteHandler();

        writableFileState = writableFileStateWithOneKey();
        given(writableStates.<FileID, File>get(FILES)).willReturn(writableFileState);
        writableStore = new WritableFileStore(writableStates);
        testConfig = HederaTestConfigBuilder.createConfig();
        lenient().when(preHandleContext.configuration()).thenReturn(testConfig);
        lenient().when(handleContext.configuration()).thenReturn(testConfig);
        when(mockStoreFactory.getStore(ReadableFileStore.class)).thenReturn(mockStore);
        when(mockStoreFactory.getStore(ReadableAccountStore.class)).thenReturn(accountStore);
    }

    @Test
    @DisplayName("File not found returns error")
    void fileIdNotFound() throws PreCheckException {
        // given:
        mockPayerLookup();
        given(mockStore.getFileMetadata(notNull())).willReturn(null);
        final var context =
                new PreHandleContextImpl(mockStoreFactory, newFileUnDeleteTxn(), testConfig, mockDispatcher);

        // when:
        assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_FILE_ID);
    }

    @Test
    @DisplayName("Pre handle works as expected")
    void preHandleWorksAsExpected() throws PreCheckException {
        refreshStoresWithCurrentFileOnlyInReadable();
        BDDMockito.given(accountStore.getAccountById(payerId)).willReturn(payerAccount);
        BDDMockito.given(mockStoreFactory.getStore(ReadableFileStore.class)).willReturn(readableStore);
        BDDMockito.given(mockStoreFactory.getStore(ReadableAccountStore.class)).willReturn(accountStore);
        BDDMockito.given(payerAccount.key()).willReturn(A_COMPLEX_KEY);

        PreHandleContext realPreContext =
                new PreHandleContextImpl(mockStoreFactory, newFileUnDeleteTxn(), testConfig, mockDispatcher);

        subject.preHandle(realPreContext);

        assertTrue(realPreContext.requiredNonPayerKeys().size() > 0);
        assertEquals(3, realPreContext.requiredNonPayerKeys().size());
    }

    @Test
    @DisplayName("Fails handle if file doesn't exist")
    void fileDoesntExist() {
        given(handleContext.body()).willReturn(newFileUnDeleteTxn());

        writableFileState = emptyWritableFileState();
        given(writableStates.<FileID, File>get(FILES)).willReturn(writableFileState);
        writableStore = new WritableFileStore(writableStates);
        given(handleContext.writableStore(WritableFileStore.class)).willReturn(writableStore);

        final var msg = assertThrows(HandleException.class, () -> subject.handle(handleContext));
        assertEquals(INVALID_FILE_ID, msg.getStatus());
    }

    @Test
    @DisplayName("Fails handle if the file is a system file")
    void fileIsNotSystemFile() {
        given(handleContext.body()).willReturn(newSystemDeleteTxn());

        final var existingFile = writableStore.get(fileId);
        assertTrue(existingFile.isPresent());
        assertFalse(existingFile.get().deleted());
        given(handleContext.writableStore(WritableFileStore.class)).willReturn(writableStore);

        final var msg = assertThrows(HandleException.class, () -> subject.handle(handleContext));
        assertEquals(INVALID_FILE_ID, msg.getStatus());
    }

    @Test
    @DisplayName("Fails handle if keys doesn't exist on file system to be deleted")
    void keysDoesntExist() {
        given(handleContext.body()).willReturn(newFileUnDeleteTxn());
        file = new File(fileId, expirationTime, null, Bytes.wrap(contents), memo, false);

        writableFileState = writableFileStateWithOneKey();
        given(writableStates.<FileID, File>get(FILES)).willReturn(writableFileState);
        writableStore = new WritableFileStore(writableStates);
        given(handleContext.writableStore(WritableFileStore.class)).willReturn(writableStore);

        final var msg = assertThrows(HandleException.class, () -> subject.handle(handleContext));

        assertEquals(UNAUTHORIZED, msg.getStatus());
    }

    @Test
    @DisplayName("Handle works as expected and file  deleted when time is expired(less than epoch second)")
    void handleWorksAsExpectedWhenExpirationTimeIsExpired() {
        given(handleContext.body()).willReturn(newFileUnDeleteTxn());

        final var existingFile = writableStore.get(fileId);
        assertTrue(existingFile.isPresent());
        assertFalse(existingFile.get().deleted());
        given(handleContext.writableStore(WritableFileStore.class)).willReturn(writableStore);

        lenient().when(handleContext.consensusNow()).thenReturn(instant);
        lenient().when(instant.getEpochSecond()).thenReturn(existingFile.get().expirationSecond() + 100);
        subject.handle(handleContext);

        final var changedFile = writableStore.get(fileId);

        assertEquals(changedFile, Optional.empty());
    }

    @Test
    @DisplayName("Handle works as expected and the system file marked as undeleted")
    void handleWorksAsExpectedWhenExpirationTimeIsNotExpired() {
        given(handleContext.body()).willReturn(newFileUnDeleteTxn());

        final var existingFile = writableStore.get(fileSystemFileId);
        assertTrue(existingFile.isPresent());
        assertFalse(existingFile.get().deleted());
        given(handleContext.writableStore(WritableFileStore.class)).willReturn(writableStore);

        lenient().when(handleContext.consensusNow()).thenReturn(instant);
        lenient().when(instant.getEpochSecond()).thenReturn(existingFile.get().expirationSecond() - 100);
        subject.handle(handleContext);

        final var changedFile = writableStore.get(fileSystemFileId);

        assertTrue(changedFile.isPresent());
        assertFalse(changedFile.get().deleted());
    }

    private Key mockPayerLookup() throws PreCheckException {
        return FileTestUtils.mockPayerLookup(A_COMPLEX_KEY, payerId, accountStore);
    }

    private TransactionBody newSystemDeleteTxn() {
        final var txnId = TransactionID.newBuilder().accountID(payerId).build();
        final var deleteFileSystemBuilder =
                SystemUndeleteTransactionBody.newBuilder().fileID(WELL_KNOWN_SYSTEM_FILE_ID);
        return TransactionBody.newBuilder()
                .transactionID(txnId)
                .systemUndelete(deleteFileSystemBuilder.build())
                .build();
    }

    private TransactionBody newFileUnDeleteTxn() {
        final var txnId = TransactionID.newBuilder().accountID(payerId).build();
        final var deleteFileSystemBuilder =
                SystemUndeleteTransactionBody.newBuilder().fileID(WELL_KNOWN_FILE_ID);
        return TransactionBody.newBuilder()
                .transactionID(txnId)
                .systemUndelete(deleteFileSystemBuilder.build())
                .build();
    }
}
