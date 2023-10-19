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

package com.hedera.node.app.service.networkadmin.impl.test.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_ID;
import static com.hedera.node.app.spi.fixtures.Assertions.assertThrowsPreCheck;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.QueryHeader;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.ResponseHeader;
import com.hedera.hapi.node.base.ResponseType;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.Response;
import com.hedera.hapi.node.transaction.TransactionGetRecordQuery;
import com.hedera.hapi.node.transaction.TransactionGetRecordResponse;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.node.app.service.networkadmin.impl.handlers.NetworkTransactionGetRecordHandler;
import com.hedera.node.app.spi.workflows.QueryContext;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NetworkTransactionGetRecordHandlerTest extends NetworkAdminHandlerTestBase {
    @Mock
    private QueryContext context;

    private NetworkTransactionGetRecordHandler networkTransactionGetRecordHandler;

    @BeforeEach
    void setUp() {
        networkTransactionGetRecordHandler = new NetworkTransactionGetRecordHandler();
        final var configuration = HederaTestConfigBuilder.createConfig();
        lenient().when(context.configuration()).thenReturn(configuration);
    }

    @Test
    void extractsHeader() {
        final var query = createGetTransactionRecordQuery(transactionID, false, false);
        final var header = networkTransactionGetRecordHandler.extractHeader(query);
        final var op = query.transactionGetRecordOrThrow();
        assertEquals(op.header(), header);
    }

    @Test
    void createsEmptyResponse() {
        final var responseHeader = ResponseHeader.newBuilder()
                .nodeTransactionPrecheckCode(ResponseCodeEnum.FAIL_FEE)
                .build();
        final var response = networkTransactionGetRecordHandler.createEmptyResponse(responseHeader);
        final var expectedResponse = Response.newBuilder()
                .transactionGetRecord(TransactionGetRecordResponse.newBuilder().header(responseHeader))
                .build();
        assertEquals(expectedResponse, response);
    }

    @Test
    void requiresPayment() {
        assertTrue(networkTransactionGetRecordHandler.requiresNodePayment(ResponseType.ANSWER_ONLY));
        assertTrue(networkTransactionGetRecordHandler.requiresNodePayment(ResponseType.ANSWER_STATE_PROOF));
        assertFalse(networkTransactionGetRecordHandler.requiresNodePayment(ResponseType.COST_ANSWER));
        assertFalse(networkTransactionGetRecordHandler.requiresNodePayment(ResponseType.COST_ANSWER_STATE_PROOF));
    }

    @Test
    void needsAnswerOnlyCostForCostAnswer() {
        assertFalse(networkTransactionGetRecordHandler.needsAnswerOnlyCost(ResponseType.ANSWER_ONLY));
        assertFalse(networkTransactionGetRecordHandler.needsAnswerOnlyCost(ResponseType.ANSWER_STATE_PROOF));
        assertTrue(networkTransactionGetRecordHandler.needsAnswerOnlyCost(ResponseType.COST_ANSWER));
        assertFalse(networkTransactionGetRecordHandler.needsAnswerOnlyCost(ResponseType.COST_ANSWER_STATE_PROOF));
    }

    @Test
    void validatesQueryWhenValidRecord() throws Throwable {

        final var query = createGetTransactionRecordQuery(transactionID, false, false);
        given(context.query()).willReturn(query);

        assertThatCode(() -> networkTransactionGetRecordHandler.validate(context))
                .doesNotThrowAnyException();
    }

    @Test
    void validatesQueryWhenNoTransactionId() throws Throwable {

        final var query = createEmptysQuery();
        given(context.query()).willReturn(query);

        assertThrowsPreCheck(() -> networkTransactionGetRecordHandler.validate(context), INVALID_TRANSACTION_ID);
    }

    @Test
    void validatesQueryWhenNoAccountId() throws Throwable {

        final var query = createGetTransactionRecordQuery(transactionIDWithoutAccount(0, 0), false, false);
        given(context.query()).willReturn(query);

        assertThrowsPreCheck(() -> networkTransactionGetRecordHandler.validate(context), INVALID_ACCOUNT_ID);
    }

    @Test
    void getsResponseIfFailedResponse() {
        final var responseHeader = ResponseHeader.newBuilder()
                .nodeTransactionPrecheckCode(ResponseCodeEnum.FAIL_FEE)
                .build();

        final var query = createGetTransactionRecordQuery(transactionID, false, false);
        when(context.query()).thenReturn(query);

        final var response = networkTransactionGetRecordHandler.findResponse(context, responseHeader);
        final var op = response.transactionGetRecordOrThrow();
        assertEquals(ResponseCodeEnum.FAIL_FEE, op.header().nodeTransactionPrecheckCode());
        assertNull(op.transactionRecord());
    }

    @Test
    void getsResponseIsEmptyWhenTransactionNotExist() {
        final var responseHeader = ResponseHeader.newBuilder()
                .nodeTransactionPrecheckCode(ResponseCodeEnum.OK)
                .build();

        final var query = createGetTransactionRecordQuery(transactionIDNotInCache, false, false);
        when(context.query()).thenReturn(query);
        when(context.recordCache()).thenReturn(cache);

        final var response = networkTransactionGetRecordHandler.findResponse(context, responseHeader);
        final var op = response.transactionGetRecordOrThrow();
        assertEquals(ResponseCodeEnum.RECORD_NOT_FOUND, op.header().nodeTransactionPrecheckCode());
        assertNull(op.transactionRecord());
    }

    @Test
    void getsResponseIfOkResponse() {
        final var responseHeader = ResponseHeader.newBuilder()
                .nodeTransactionPrecheckCode(ResponseCodeEnum.OK)
                .build();
        final var expectedRecord = getExpectedRecord(transactionID);

        final var query = createGetTransactionRecordQuery(transactionID, false, false);
        when(context.query()).thenReturn(query);
        when(context.recordCache()).thenReturn(cache);

        final var response = networkTransactionGetRecordHandler.findResponse(context, responseHeader);
        final var op = response.transactionGetRecordOrThrow();
        assertEquals(ResponseCodeEnum.OK, op.header().nodeTransactionPrecheckCode());
        assertEquals(expectedRecord, op.transactionRecord());
    }

    @Test
    void getsResponseIfOkResponseWithDuplicates() {
        final var responseHeader = ResponseHeader.newBuilder()
                .nodeTransactionPrecheckCode(ResponseCodeEnum.OK)
                .build();
        final var expectedRecord = getExpectedRecord(transactionID);
        final List<TransactionRecord> expectedDuplicateRecords = getExpectedDuplicateList();

        final var query = createGetTransactionRecordQuery(transactionID, true, false);
        when(context.query()).thenReturn(query);
        when(context.recordCache()).thenReturn(cache);

        final var response = networkTransactionGetRecordHandler.findResponse(context, responseHeader);
        final var op = response.transactionGetRecordOrThrow();
        assertEquals(ResponseCodeEnum.OK, op.header().nodeTransactionPrecheckCode());
        assertEquals(expectedRecord, op.transactionRecord());
        assertEquals(expectedDuplicateRecords, op.duplicateTransactionRecords());
        assertEquals(
                expectedDuplicateRecords.size(),
                op.duplicateTransactionRecords().size());
    }

    @Test
    void getsResponseIfOkResponseWithChildrenRecord() {
        final var responseHeader = ResponseHeader.newBuilder()
                .nodeTransactionPrecheckCode(ResponseCodeEnum.OK)
                .build();
        final var expectedRecord = getExpectedRecord(transactionID);
        final List<TransactionRecord> expectedChildRecordList = getExpectedChildRecordList();

        final var query = createGetTransactionRecordQuery(transactionID, false, true);
        when(context.query()).thenReturn(query);
        when(context.recordCache()).thenReturn(cache);

        final var response = networkTransactionGetRecordHandler.findResponse(context, responseHeader);
        final var op = response.transactionGetRecordOrThrow();
        assertEquals(ResponseCodeEnum.OK, op.header().nodeTransactionPrecheckCode());
        assertEquals(expectedRecord, op.transactionRecord());
        assertEquals(expectedChildRecordList, op.childTransactionRecords());
        assertEquals(
                expectedChildRecordList.size(), op.childTransactionRecords().size());
    }

    private TransactionRecord getExpectedRecord(TransactionID transactionID) {
        return primaryRecord;
    }

    private List<TransactionRecord> getExpectedDuplicateList() {
        return List.of(duplicate1, duplicate2, duplicate3);
    }

    private List<TransactionRecord> getExpectedChildRecordList() {
        return List.of(recordOne, recordTwo, recordThree);
    }

    private Query createGetTransactionRecordQuery(
            final TransactionID transactionID, final boolean includeDuplicates, final boolean includeChildRecords) {
        final var data = TransactionGetRecordQuery.newBuilder()
                .transactionID(transactionID)
                .includeDuplicates(includeDuplicates)
                .includeChildRecords(includeChildRecords)
                .header(QueryHeader.newBuilder().build())
                .build();

        return Query.newBuilder().transactionGetRecord(data).build();
    }

    private Query createEmptysQuery() {
        final var data = TransactionGetRecordQuery.newBuilder()
                .header(QueryHeader.newBuilder().build())
                .build();

        return Query.newBuilder().transactionGetRecord(data).build();
    }
}
