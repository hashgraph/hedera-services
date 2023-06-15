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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.file.FileAppendTransactionBody;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.file.impl.WritableFileStoreImpl;
import com.hedera.node.app.service.file.impl.handlers.FileAppendHandler;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryMeta;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.config.data.FilesConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileAppendHandlerTest extends FileHandlerTestBase {
    private static final FileID WELL_KNOWN_FILE_ID =
            FileID.newBuilder().fileNum(1L).build();

    private final FileAppendTransactionBody.Builder OP_BUILDER = FileAppendTransactionBody.newBuilder();

    private final ExpiryMeta currentExpiryMeta = new ExpiryMeta(expirationTime, NA, NA);

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

    private FileAppendHandler subject;
    private FilesConfig config;

    @BeforeEach
    void setUp() {
        subject = new FileAppendHandler();
        config = new FilesConfig(101L, 121L, 112L, 111L, 122L, 102L, 123L, 1000000L, 1024);
        lenient().when(handleContext.configuration()).thenReturn(configuration);
        lenient().when(configuration.getConfigData(FilesConfig.class)).thenReturn(config);
    }

    @Test
    void rejectsMissingFile() {
        final var txBody = TransactionBody.newBuilder().fileAppend(OP_BUILDER).build();
        when(handleContext.body()).thenReturn(txBody);

        // expect:
        assertFailsWith(INVALID_FILE_ID, () -> subject.handle(handleContext));
    }

    @Test
    void rejectsDeletedFile() {
        givenValidFile(true);
        refreshStoresWithCurrentFileInBothReadableAndWritable();

        final var txBody = TransactionBody.newBuilder()
                .fileAppend(OP_BUILDER.fileID(wellKnownId()))
                .build();
        when(handleContext.body()).thenReturn(txBody);

        // expect:
        assertFailsWith(FILE_DELETED, () -> subject.handle(handleContext));
    }

    @Test
    void validatesNewContent() {
        givenValidFile(false);
        refreshStoresWithCurrentFileInBothReadableAndWritable();

        final var txBody = TransactionBody.newBuilder()
                .fileAppend(OP_BUILDER.fileID(wellKnownId()).contents(Bytes.wrap(new byte[1048577])))
                .build();
        given(handleContext.body()).willReturn(txBody);
        given(handleContext.writableStore(WritableFileStoreImpl.class)).willReturn(writableStore);
        // expect:
        assertFailsWith(ResponseCodeEnum.MAX_FILE_SIZE_EXCEEDED, () -> subject.handle(handleContext));
    }

    @Test
    void validatesNewContentEmptyRemainSameContent() {
        givenValidFile(false);
        refreshStoresWithCurrentFileInBothReadableAndWritable();

        final var txBody = TransactionBody.newBuilder()
                .fileAppend(OP_BUILDER.fileID(wellKnownId()).contents(Bytes.wrap(new byte[0])))
                .build();
        given(handleContext.body()).willReturn(txBody);
        given(handleContext.writableStore(WritableFileStoreImpl.class)).willReturn(writableStore);

        // expect:
        subject.handle(handleContext);

        final var appendedFile = writableFileState.get(fileId);
        assertEquals(file.contents(), appendedFile.contents());
    }

    @Test
    void appliesNewContent() {
        final var additionalContent = "STUFF".getBytes();
        var bytesNewContent = Bytes.wrap(additionalContent);
        givenValidFile(false);
        refreshStoresWithCurrentFileInBothReadableAndWritable();

        var newContent = ArrayUtils.addAll(contents, additionalContent);
        var bytesNewContentExpected = Bytes.wrap(newContent);
        final var txBody = TransactionBody.newBuilder()
                .fileAppend(OP_BUILDER.fileID(wellKnownId()).contents(bytesNewContent))
                .build();
        given(handleContext.body()).willReturn(txBody);
        given(handleContext.writableStore(WritableFileStoreImpl.class)).willReturn(writableStore);

        subject.handle(handleContext);

        final var appendedFile = writableFileState.get(fileId);
        assertEquals(bytesNewContentExpected, appendedFile.contents());
    }

    @Test
    void nothingHappensIfUpdateIsNoop() {
        refreshStoresWithCurrentFileInBothReadableAndWritable();

        // No-op
        final var txBody = TransactionBody.newBuilder()
                .fileAppend(OP_BUILDER.fileID(wellKnownId()))
                .build();
        given(handleContext.body()).willReturn(txBody);
        given(handleContext.writableStore(WritableFileStoreImpl.class)).willReturn(writableStore);

        subject.handle(handleContext);

        final var appendedFile = writableFileState.get(fileId);
        assertEquals(file, appendedFile);
    }

    public static void assertFailsWith(final ResponseCodeEnum status, final Runnable something) {
        final var ex = assertThrows(HandleException.class, something::run);
        assertEquals(status, ex.getStatus());
    }

    private TransactionBody txnWith(final FileAppendTransactionBody op) {
        final var txnId = TransactionID.newBuilder().accountID(payerId).build();
        final var appendFileBuilder = FileAppendTransactionBody.newBuilder().fileID(WELL_KNOWN_FILE_ID);
        return TransactionBody.newBuilder()
                .transactionID(txnId)
                .fileAppend(appendFileBuilder.build())
                .build();
    }

    private FileID wellKnownId() {
        return FileID.newBuilder().fileNum(fileId.fileNum()).build();
    }
}
