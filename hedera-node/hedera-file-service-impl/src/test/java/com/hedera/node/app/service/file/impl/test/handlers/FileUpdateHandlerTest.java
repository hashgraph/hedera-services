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

import static com.hedera.hapi.node.base.ResponseCodeEnum.FILE_DELETED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hedera.node.app.spi.validation.ExpiryMeta.NA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.file.FileUpdateTransactionBody;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.file.impl.WritableFileStoreImpl;
import com.hedera.node.app.service.file.impl.handlers.FileUpdateHandler;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryMeta;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.config.data.FilesConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileUpdateHandlerTest extends FileHandlerTestBase {
    private static final FileID WELL_KNOWN_FILE_ID =
            FileID.newBuilder().fileNum(1L).build();

    private final FileUpdateTransactionBody.Builder OP_BUILDER = FileUpdateTransactionBody.newBuilder();

    private final ExpiryMeta currentExpiryMeta = new ExpiryMeta(expirationTime, NA, NA);

    @Mock
    private ReadableAccountStore accountStore;

    @Mock
    private Account account;

    @Mock
    private Account autoRenewAccount;

    @Mock
    private ExpiryValidator expiryValidator;

    @Mock
    private AttributeValidator attributeValidator;

    @Mock
    private Configuration configuration;

    private FileUpdateHandler subject;
    private FilesConfig config;

    @BeforeEach
    void setUp() {
        subject = new FileUpdateHandler();
        config = new FilesConfig(101L, 121L, 112L, 111L, 122L, 102L, 123L, 1000000L, 1024);
        lenient().when(handleContext.configuration()).thenReturn(configuration);
        lenient().when(configuration.getConfigData(FilesConfig.class)).thenReturn(config);
    }

    @Test
    void rejectsMissingFile() {
        final var op = OP_BUILDER.build();
        final var txBody = TransactionBody.newBuilder().fileUpdate(op).build();
        when(handleContext.body()).thenReturn(txBody);

        // expect:
        assertFailsWith(INVALID_FILE_ID, () -> subject.handle(handleContext));
    }

    @Test
    void rejectsDeletedFile() {
        givenValidFile(true);
        refreshStoresWithCurrentFileInBothReadableAndWritable();

        final var op = OP_BUILDER.fileID(wellKnownId()).build();
        final var txBody = TransactionBody.newBuilder().fileUpdate(op).build();
        when(handleContext.body()).thenReturn(txBody);

        // expect:
        assertFailsWith(FILE_DELETED, () -> subject.handle(handleContext));
    }

    @Test
    void rejectsNonExpiryMutationOfImmutableFile() {
        givenValidFile(false, false);
        refreshStoresWithCurrentFileInBothReadableAndWritable();

        final var op =
                OP_BUILDER.fileID(wellKnownId()).memo("Please mind the vase").build();
        final var txBody = TransactionBody.newBuilder().fileUpdate(op).build();
        when(handleContext.body()).thenReturn(txBody);

        // expect:
        assertFailsWith(ResponseCodeEnum.UNAUTHORIZED, () -> subject.handle(handleContext));
    }

    @Test
    void appliesNewKeys() {
        givenValidFile(false);
        refreshStoresWithCurrentFileInBothReadableAndWritable();

        final var op = OP_BUILDER.fileID(wellKnownId()).keys(anotherKeys).build();
        final var txBody = TransactionBody.newBuilder().fileUpdate(op).build();
        when(handleContext.body()).thenReturn(txBody);
        given(handleContext.attributeValidator()).willReturn(attributeValidator);
        given(handleContext.body())
                .willReturn(TransactionBody.newBuilder().fileUpdate(op).build());
        given(handleContext.writableStore(WritableFileStoreImpl.class)).willReturn(writableStore);

        subject.handle(handleContext);

        final var newFile = writableFileState.get(fileId);
        final var expectedKey = anotherKeys;
        assertEquals(expectedKey, newFile.keys());
    }

    @Test
    void validatesNewMemo() {
        givenValidFile(false);
        refreshStoresWithCurrentFileInBothReadableAndWritable();

        final var op =
                OP_BUILDER.fileID(wellKnownId()).memo("Please mind the vase").build();
        final var txBody = TransactionBody.newBuilder().fileUpdate(op).build();
        when(handleContext.body()).thenReturn(txBody);
        given(handleContext.attributeValidator()).willReturn(attributeValidator);
        willThrow(new HandleException(ResponseCodeEnum.MEMO_TOO_LONG))
                .given(attributeValidator)
                .validateMemo(txBody.fileUpdate().memo());

        // expect:
        assertFailsWith(ResponseCodeEnum.MEMO_TOO_LONG, () -> subject.handle(handleContext));
    }

    @Test
    void appliesNewMemo() {
        final var newMemo = "Please mind the vase";
        givenValidFile(false);
        refreshStoresWithCurrentFileInBothReadableAndWritable();

        final var op = OP_BUILDER.fileID(wellKnownId()).memo(newMemo).build();
        final var txBody = TransactionBody.newBuilder().fileUpdate(op).build();
        when(handleContext.body()).thenReturn(txBody);
        given(handleContext.attributeValidator()).willReturn(attributeValidator);
        subject.handle(handleContext);

        final var newFile = writableFileState.get(fileId);
        assertEquals(newMemo, newFile.memo());
    }

    @Test
    void validatesNewContent() {
        givenValidFile(false);
        refreshStoresWithCurrentFileInBothReadableAndWritable();

        final var op = OP_BUILDER
                .fileID(wellKnownId())
                .contents(Bytes.wrap(new byte[1048577]))
                .build();
        final var txBody = TransactionBody.newBuilder().fileUpdate(op).build();
        when(handleContext.body()).thenReturn(txBody);
        given(handleContext.attributeValidator()).willReturn(attributeValidator);

        // expect:
        assertFailsWith(ResponseCodeEnum.MAX_FILE_SIZE_EXCEEDED, () -> subject.handle(handleContext));
    }

    @Test
    void validatesNewContentEmptyRemainSameContent() {
        givenValidFile(false);
        refreshStoresWithCurrentFileInBothReadableAndWritable();

        final var op = OP_BUILDER
                .fileID(wellKnownId())
                .contents(Bytes.wrap(new byte[0]))
                .build();
        final var txBody = TransactionBody.newBuilder().fileUpdate(op).build();
        when(handleContext.body()).thenReturn(txBody);

        // expect:
        subject.handle(handleContext);

        final var updatedFile = writableFileState.get(fileId);
        assertEquals(file.contents(), updatedFile.contents());
    }

    @Test
    void appliesNewContent() {
        final var newContent = Bytes.wrap("STUFF".getBytes());
        givenValidFile(false);
        refreshStoresWithCurrentFileInBothReadableAndWritable();

        final var op = OP_BUILDER.fileID(wellKnownId()).contents(newContent).build();
        final var txBody = TransactionBody.newBuilder().fileUpdate(op).build();
        when(handleContext.body()).thenReturn(txBody);
        given(handleContext.attributeValidator()).willReturn(attributeValidator);
        subject.handle(handleContext);

        final var newFile = writableFileState.get(fileId);
        assertEquals(newContent, newFile.contents());
    }

    @Test
    void appliesNewExpiryViaMeta() {
        refreshStoresWithCurrentFileInBothReadableAndWritable();

        final var expiry = Timestamp.newBuilder().seconds(1_234_568L).build();
        final var op = OP_BUILDER.fileID(wellKnownId()).expirationTime(expiry).build();
        final var txBody = TransactionBody.newBuilder().fileUpdate(op).build();
        when(handleContext.body()).thenReturn(txBody);
        subject.handle(handleContext);

        final var newFile = writableFileState.get(fileId);
        assertEquals(1_234_568L, newFile.expirationTime());
    }

    @Test
    void appliesNewExpiryLowerExpirationTimeViaMeta() {
        refreshStoresWithCurrentFileInBothReadableAndWritable();

        final var expiry = Timestamp.newBuilder().seconds(1_234_566L).build();
        final var op = OP_BUILDER.fileID(wellKnownId()).expirationTime(expiry).build();
        final var txBody = TransactionBody.newBuilder().fileUpdate(op).build();
        when(handleContext.body()).thenReturn(txBody);
        subject.handle(handleContext);

        final var newFile = writableFileState.get(fileId);
        assertEquals(1_234_567L, newFile.expirationTime());
    }

    @Test
    void nothingHappensIfUpdateIsNoop() {
        refreshStoresWithCurrentFileInBothReadableAndWritable();

        // No-op
        final var op = OP_BUILDER.fileID(wellKnownId()).build();
        final var txBody = TransactionBody.newBuilder().fileUpdate(op).build();
        when(handleContext.body()).thenReturn(txBody);

        subject.handle(handleContext);

        final var newFile = writableFileState.get(fileId);
        assertEquals(file, newFile);
    }

    public static void assertFailsWith(final ResponseCodeEnum status, final Runnable something) {
        final var ex = assertThrows(HandleException.class, something::run);
        assertEquals(status, ex.getStatus());
    }

    @Test
    void memoMutationsIsNonExpiry() {
        final var op = OP_BUILDER.memo("HI").build();
        assertTrue(FileUpdateHandler.wantsToMutateNonExpiryField(op));
    }

    @Test
    void keysMutationIsNonExpiry() {
        final var op = OP_BUILDER.keys(keys).build();
        assertTrue(FileUpdateHandler.wantsToMutateNonExpiryField(op));
    }

    @Test
    void expiryMutationIsExpiry() {
        final var expiryTime = Timestamp.newBuilder().seconds(123L).build();
        final var op = OP_BUILDER.expirationTime(expiryTime).build();
        assertFalse(FileUpdateHandler.wantsToMutateNonExpiryField(op));
    }

    private TransactionBody txnWith(final FileUpdateTransactionBody op) {
        final var txnId = TransactionID.newBuilder().accountID(payerId).build();
        final var updateFileBuilder = FileUpdateTransactionBody.newBuilder().fileID(WELL_KNOWN_FILE_ID);
        return TransactionBody.newBuilder()
                .transactionID(txnId)
                .fileUpdate(updateFileBuilder.build())
                .build();
    }

    private FileID wellKnownId() {
        return fileId;
    }
}
