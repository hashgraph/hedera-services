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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hedera.node.app.spi.fixtures.Assertions.assertThrowsPreCheck;
import static com.swirlds.common.utility.CommonUtils.hex;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.QueryHeader;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.ResponseHeader;
import com.hedera.hapi.node.base.ResponseType;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.file.FileGetInfoQuery;
import com.hedera.hapi.node.file.FileGetInfoResponse;
import com.hedera.hapi.node.file.FileInfo;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.Response;
import com.hedera.node.app.hapi.fees.usage.file.FileOpsUsage;
import com.hedera.node.app.service.file.ReadableFileStore;
import com.hedera.node.app.service.file.ReadableUpgradeFileStore;
import com.hedera.node.app.service.file.impl.ReadableFileStoreImpl;
import com.hedera.node.app.service.file.impl.handlers.FileGetInfoHandler;
import com.hedera.node.app.service.file.impl.test.FileTestBase;
import com.hedera.node.app.spi.workflows.QueryContext;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.common.crypto.CryptographyHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileGetInfoTest extends FileTestBase {

    @Mock(strictness = LENIENT)
    private QueryContext context;

    @Mock
    private FileOpsUsage fileOpsUsage;

    private FileGetInfoHandler subject;

    @BeforeEach
    void setUp() {
        subject = new FileGetInfoHandler(fileOpsUsage);
        final var configuration = HederaTestConfigBuilder.createConfig();
        lenient().when(context.configuration()).thenReturn(configuration);
    }

    @Test
    void extractsHeader() {
        final var query = createGetFileInfoQuery(fileId.fileNum());
        final var header = subject.extractHeader(query);
        final var op = query.fileGetInfoOrThrow();
        assertEquals(op.header(), header);
    }

    @Test
    void createsEmptyResponse() {
        final var responseHeader = ResponseHeader.newBuilder()
                .nodeTransactionPrecheckCode(ResponseCodeEnum.FAIL_FEE)
                .build();
        final var response = subject.createEmptyResponse(responseHeader);
        final var expectedResponse = Response.newBuilder()
                .fileGetInfo(FileGetInfoResponse.newBuilder().header(responseHeader))
                .build();
        assertEquals(expectedResponse, response);
    }

    @Test
    void requiresPayment() {
        assertTrue(subject.requiresNodePayment(ResponseType.ANSWER_ONLY));
        assertTrue(subject.requiresNodePayment(ResponseType.ANSWER_STATE_PROOF));
        assertFalse(subject.requiresNodePayment(ResponseType.COST_ANSWER));
        assertFalse(subject.requiresNodePayment(ResponseType.COST_ANSWER_STATE_PROOF));
    }

    @Test
    void needsAnswerOnlyCostForCostAnswer() {
        assertFalse(subject.needsAnswerOnlyCost(ResponseType.ANSWER_ONLY));
        assertFalse(subject.needsAnswerOnlyCost(ResponseType.ANSWER_STATE_PROOF));
        assertTrue(subject.needsAnswerOnlyCost(ResponseType.COST_ANSWER));
        assertFalse(subject.needsAnswerOnlyCost(ResponseType.COST_ANSWER_STATE_PROOF));
    }

    @Test
    void validatesQueryWhenValidFile() {
        givenValidFile();

        final var query = createGetFileInfoQuery(fileId.fileNum());
        given(context.query()).willReturn(query);
        given(context.createStore(ReadableFileStore.class)).willReturn(readableStore);

        assertThatCode(() -> subject.validate(context)).doesNotThrowAnyException();
    }

    @Test
    void validatesQueryIfInvalidFile() {
        final var query = createGetFileInfoQuery();
        when(context.query()).thenReturn(query);

        assertThrowsPreCheck(() -> subject.validate(context), INVALID_FILE_ID);
    }

    @Test
    void validatesQueryIfDeletedFile() {
        givenValidFile(true);
        readableFileState = readableFileState();
        given(readableStates.<FileID, File>get(FILES)).willReturn(readableFileState);
        readableStore = new ReadableFileStoreImpl(readableStates);

        final var query = createGetFileInfoQuery(fileId.fileNum());
        when(context.query()).thenReturn(query);
        when(context.createStore(ReadableFileStore.class)).thenReturn(readableStore);

        assertDoesNotThrow(() -> subject.validate(context));
    }

    @Test
    void getsResponseIfFailedResponse() {
        final var responseHeader = ResponseHeader.newBuilder()
                .nodeTransactionPrecheckCode(ResponseCodeEnum.FAIL_FEE)
                .build();

        final var query = createGetFileInfoQuery(fileId.fileNum());
        when(context.query()).thenReturn(query);
        when(context.createStore(ReadableFileStore.class)).thenReturn(readableStore);

        final var response = subject.findResponse(context, responseHeader);
        final var op = response.fileGetInfoOrThrow();
        assertEquals(ResponseCodeEnum.FAIL_FEE, op.header().nodeTransactionPrecheckCode());
        assertNull(op.fileInfo());
    }

    @Test
    void getsResponseIfOkResponse() {
        givenValidFile();
        final var responseHeader = ResponseHeader.newBuilder()
                .nodeTransactionPrecheckCode(ResponseCodeEnum.OK)
                .build();
        final var expectedInfo = getExpectedInfo();

        final var query = createGetFileInfoQuery(fileId.fileNum());
        when(context.query()).thenReturn(query);
        when(context.createStore(ReadableFileStore.class)).thenReturn(readableStore);

        final var response = subject.findResponse(context, responseHeader);
        final var fileInfoResponse = response.fileGetInfoOrThrow();
        assertEquals(ResponseCodeEnum.OK, fileInfoResponse.header().nodeTransactionPrecheckCode());
        assertEquals(expectedInfo, fileInfoResponse.fileInfo());
    }

    @Test
    void getsResponseIfInvalidFileID() {
        givenValidFile();
        final var responseHeader = ResponseHeader.newBuilder()
                .nodeTransactionPrecheckCode(ResponseCodeEnum.OK)
                .build();

        final var query = createGetFileInfoQuery(fileIdNotExist.fileNum());
        when(context.query()).thenReturn(query);
        when(context.createStore(ReadableFileStore.class)).thenReturn(readableStore);

        final var response = subject.findResponse(context, responseHeader);
        final var fileInfoResponse = response.fileGetInfoOrThrow();
        assertEquals(INVALID_FILE_ID, fileInfoResponse.header().nodeTransactionPrecheckCode());
    }

    @Test
    void getsResponseIfOkResponseUpgradeFile() {
        givenValidUpgradeFile(false, true);
        refreshStoresWithCurrentFileInBothReadableAndWritable();

        final var responseHeader = ResponseHeader.newBuilder()
                .nodeTransactionPrecheckCode(ResponseCodeEnum.OK)
                .build();
        final var expectedInfo = getExpectedUpgradeInfo();

        final var query = createGetFileInfoQuery(fileUpgradeFileId.fileNum());
        when(context.query()).thenReturn(query);
        when(context.createStore(ReadableUpgradeFileStore.class)).thenReturn(readableUpgradeFileStore);
        final var response = subject.findResponse(context, responseHeader);
        final var fileInfoResponse = response.fileGetInfoOrThrow();
        assertEquals(ResponseCodeEnum.OK, fileInfoResponse.header().nodeTransactionPrecheckCode());
        assertEquals(expectedInfo, fileInfoResponse.fileInfo());
    }

    private FileInfo getExpectedInfo() {
        return FileInfo.newBuilder()
                .memo(file.memo())
                .fileID(fileId)
                .keys(keys)
                .expirationTime(Timestamp.newBuilder().seconds(file.expirationSecond()))
                .ledgerId(ledgerId)
                .deleted(false)
                .size(8)
                .build();
    }

    private FileInfo getExpectedSystemInfo() {
        final var upgradeHash = hex(CryptographyHolder.get().digestBytesSync(contents));
        return FileInfo.newBuilder()
                .memo(upgradeHash)
                .fileID(FileID.newBuilder().fileNum(fileSystemFileId.fileNum()).build())
                .keys(keys)
                .expirationTime(Timestamp.newBuilder().seconds(file.expirationSecond()))
                .ledgerId(ledgerId)
                .deleted(false)
                .size(8)
                .build();
    }

    private FileInfo getExpectedUpgradeInfo() {
        final var upgradeHash = hex(CryptographyHolder.get().digestBytesSync(contents));
        return FileInfo.newBuilder()
                .memo(upgradeHash)
                .fileID(FileID.newBuilder().fileNum(fileUpgradeFileId.fileNum()).build())
                .keys(keys)
                .expirationTime(Timestamp.newBuilder().seconds(file.expirationSecond()))
                .ledgerId(ledgerId)
                .deleted(false)
                .size(8)
                .build();
    }

    private Query createGetFileInfoQuery(final long fileId) {
        final var data = FileGetInfoQuery.newBuilder()
                .fileID(FileID.newBuilder().fileNum(fileId).build())
                .header(QueryHeader.newBuilder().payment(Transaction.DEFAULT).build())
                .build();

        return Query.newBuilder().fileGetInfo(data).build();
    }

    private Query createGetFileInfoQuery() {
        final var data = FileGetInfoQuery.newBuilder()
                .header(QueryHeader.newBuilder().payment(Transaction.DEFAULT).build())
                .build();

        return Query.newBuilder().fileGetInfo(data).build();
    }
}
