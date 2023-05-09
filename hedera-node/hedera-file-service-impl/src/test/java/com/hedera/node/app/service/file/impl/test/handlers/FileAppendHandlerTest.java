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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.file.FileAppendTransactionBody;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.file.impl.config.FileServiceConfig;
import com.hedera.node.app.service.file.impl.handlers.FileAppendHandler;
import com.hedera.node.app.service.file.impl.records.UpdateFileRecordBuilder;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryMeta;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.apache.commons.lang3.ArrayUtils;
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
    private HandleContext handleContext;

    @Mock
    private Account account;

    @Mock
    private Account autoRenewAccount;

    @Mock
    private ExpiryValidator expiryValidator;

    @Mock
    private AttributeValidator attributeValidator;

    private FileAppendHandler subject = new FileAppendHandler();

    private FileServiceConfig config = new FileServiceConfig(1000000L, 1024, 8000001L, 2592000L);

    @Test
    void returnsExpectedRecordBuilderType() {
        assertInstanceOf(UpdateFileRecordBuilder.class, subject.newRecordBuilder());
    }

    @Test
    void rejectsMissingFile() {
        final var op = OP_BUILDER.build();

        // expect:
        assertFailsWith(INVALID_FILE_ID, () -> subject.handle(op, writableStore, config));
    }

    @Test
    void rejectsDeletedFile() {
        givenValidFile(true);
        refreshStoresWithCurrentFileInBothReadableAndWritable();

        final var op = OP_BUILDER.fileID(wellKnownId()).build();

        // expect:
        assertFailsWith(FILE_DELETED, () -> subject.handle(op, writableStore, config));
    }

    @Test
    void validatesNewContent() {
        givenValidFile(false);
        refreshStoresWithCurrentFileInBothReadableAndWritable();

        final var op = OP_BUILDER
                .fileID(wellKnownId())
                .contents(Bytes.wrap(new byte[1048577]))
                .build();

        // expect:
        assertFailsWith(ResponseCodeEnum.MAX_FILE_SIZE_EXCEEDED, () -> subject.handle(op, writableStore, config));
    }

    @Test
    void validatesNewContentEmptyRemainSameContent() {
        givenValidFile(false);
        refreshStoresWithCurrentFileInBothReadableAndWritable();

        final var op = OP_BUILDER
                .fileID(wellKnownId())
                .contents(Bytes.wrap(new byte[0]))
                .build();

        // expect:
        subject.handle(op, writableStore, config);

        final var appendedFile = writableFileState.get(fileEntityNum);
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
        final var op =
                OP_BUILDER.fileID(wellKnownId()).contents(bytesNewContent).build();

        subject.handle(op, writableStore, config);

        final var appendedFile = writableFileState.get(fileEntityNum);
        assertEquals(bytesNewContentExpected, appendedFile.contents());
    }

    @Test
    void nothingHappensIfUpdateIsNoop() {
        refreshStoresWithCurrentFileInBothReadableAndWritable();

        // No-op
        final var op = OP_BUILDER.fileID(wellKnownId()).build();

        subject.handle(op, writableStore, config);

        final var appendedFile = writableFileState.get(fileEntityNum);
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
        return FileID.newBuilder().fileNum(fileEntityNum.longValue()).build();
    }
}
