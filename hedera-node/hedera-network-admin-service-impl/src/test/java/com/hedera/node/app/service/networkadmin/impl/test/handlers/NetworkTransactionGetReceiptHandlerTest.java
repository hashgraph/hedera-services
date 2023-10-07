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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_ID;
import static com.hedera.node.app.spi.fixtures.Assertions.assertThrowsPreCheck;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.QueryHeader;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.ResponseHeader;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.Response;
import com.hedera.hapi.node.transaction.TransactionGetReceiptQuery;
import com.hedera.hapi.node.transaction.TransactionGetReceiptResponse;
import com.hedera.hapi.node.transaction.TransactionReceipt;
import com.hedera.node.app.service.networkadmin.impl.handlers.NetworkTransactionGetReceiptHandler;
import com.hedera.node.app.spi.workflows.QueryContext;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NetworkTransactionGetReceiptHandlerTest extends NetworkAdminHandlerTestBase {
    @Mock
    private QueryContext context;

    private NetworkTransactionGetReceiptHandler networkTransactionGetReceiptHandler;

    @BeforeEach
    void setUp() {
        networkTransactionGetReceiptHandler = new NetworkTransactionGetReceiptHandler();
        final var configuration = HederaTestConfigBuilder.createConfig();
        lenient().when(context.configuration()).thenReturn(configuration);
    }

    @Test
    void extractsHeader() {
        final var query = createGetTransactionReceiptQuery(transactionID, false, false);
        final var header = networkTransactionGetReceiptHandler.extractHeader(query);
        final var op = query.transactionGetReceiptOrThrow();
        assertEquals(op.header(), header);
    }

    @Test
    void createsEmptyResponse() {
        final var responseHeader = ResponseHeader.newBuilder()
                .nodeTransactionPrecheckCode(ResponseCodeEnum.FAIL_FEE)
                .build();
        final var response = networkTransactionGetReceiptHandler.createEmptyResponse(responseHeader);
        final var expectedResponse = Response.newBuilder()
                .transactionGetReceipt(
                        TransactionGetReceiptResponse.newBuilder().header(responseHeader))
                .build();
        assertEquals(expectedResponse, response);
    }

    @Test
    void validatesQueryWhenValidReceipt() throws Throwable {

        final var query = createGetTransactionReceiptQuery(transactionID, false, false);
        given(context.query()).willReturn(query);

        assertThatCode(() -> networkTransactionGetReceiptHandler.validate(context))
                .doesNotThrowAnyException();
    }

    @Test
    void validatesQueryWhenNoTransactionId() throws Throwable {

        final var query = createEmptysQuery();
        given(context.query()).willReturn(query);

        assertThrowsPreCheck(() -> networkTransactionGetReceiptHandler.validate(context), INVALID_TRANSACTION_ID);
    }

    @Test
    void getsResponseIfFailedResponse() {
        final var responseHeader = ResponseHeader.newBuilder()
                .nodeTransactionPrecheckCode(ResponseCodeEnum.FAIL_FEE)
                .build();

        final var query = createGetTransactionReceiptQuery(transactionID, false, false);
        when(context.query()).thenReturn(query);
        when(context.recordCache()).thenReturn(cache);

        final var response = networkTransactionGetReceiptHandler.findResponse(context, responseHeader);
        final var op = response.transactionGetReceiptOrThrow();
        assertEquals(ResponseCodeEnum.FAIL_FEE, op.header().nodeTransactionPrecheckCode());
        assertNull(op.receipt());
    }

    @Test
    void getsResponseIsEmptyWhenTransactionNotExist() {
        final var responseHeader = ResponseHeader.newBuilder()
                .nodeTransactionPrecheckCode(ResponseCodeEnum.OK)
                .build();

        final var query = createGetTransactionReceiptQuery(transactionIDNotInCache, false, false);
        when(context.query()).thenReturn(query);
        when(context.recordCache()).thenReturn(cache);

        final var response = networkTransactionGetReceiptHandler.findResponse(context, responseHeader);
        final var op = response.transactionGetReceiptOrThrow();
        assertEquals(ResponseCodeEnum.RECEIPT_NOT_FOUND, op.header().nodeTransactionPrecheckCode());
        assertNull(op.receipt());
    }

    @Test
    void getsResponseIfOkResponse() {
        final var responseHeader = ResponseHeader.newBuilder()
                .nodeTransactionPrecheckCode(ResponseCodeEnum.OK)
                .build();
        final var expectedReceipt = getExpectedReceipt();

        final var query = createGetTransactionReceiptQuery(transactionID, false, false);
        when(context.query()).thenReturn(query);
        when(context.recordCache()).thenReturn(cache);

        final var response = networkTransactionGetReceiptHandler.findResponse(context, responseHeader);
        final var op = response.transactionGetReceiptOrThrow();
        assertEquals(ResponseCodeEnum.OK, op.header().nodeTransactionPrecheckCode());
        assertEquals(expectedReceipt, op.receipt());
    }

    @Test
    void getsResponseIfOkResponseWithDuplicates() {
        final var responseHeader = ResponseHeader.newBuilder()
                .nodeTransactionPrecheckCode(ResponseCodeEnum.OK)
                .build();
        final var expectedReceipt = getExpectedReceipt();
        final List<TransactionReceipt> expectedDuplicateReceipt = getExpectedDuplicateList();

        final var query = createGetTransactionReceiptQuery(transactionID, true, false);
        when(context.query()).thenReturn(query);
        when(context.recordCache()).thenReturn(cache);

        final var response = networkTransactionGetReceiptHandler.findResponse(context, responseHeader);
        final var op = response.transactionGetReceiptOrThrow();
        assertEquals(ResponseCodeEnum.OK, op.header().nodeTransactionPrecheckCode());
        assertEquals(expectedReceipt, op.receipt());
        assertEquals(expectedDuplicateReceipt, op.duplicateTransactionReceipts());
        assertEquals(
                expectedDuplicateReceipt.size(),
                op.duplicateTransactionReceipts().size());
    }

    @Test
    void getsResponseIfOkResponseWithChildrenReceipt() {
        final var responseHeader = ResponseHeader.newBuilder()
                .nodeTransactionPrecheckCode(ResponseCodeEnum.OK)
                .build();
        final var expectedReceipt = getExpectedReceipt();
        final List<TransactionReceipt> expectedChildReceiptList = getExpectedChildReceiptList();

        final var query = createGetTransactionReceiptQuery(transactionID, false, true);
        when(context.query()).thenReturn(query);
        when(context.recordCache()).thenReturn(cache);

        final var response = networkTransactionGetReceiptHandler.findResponse(context, responseHeader);
        final var op = response.transactionGetReceiptOrThrow();
        assertEquals(ResponseCodeEnum.OK, op.header().nodeTransactionPrecheckCode());
        assertEquals(expectedReceipt, op.receipt());
        assertEquals(expectedChildReceiptList, op.childTransactionReceipts());
        assertEquals(
                expectedChildReceiptList.size(), op.childTransactionReceipts().size());
    }

    private TransactionReceipt getExpectedReceipt() {
        return primaryRecord.receipt();
    }

    private List<TransactionReceipt> getExpectedDuplicateList() {
        return List.of(duplicate1.receipt(), duplicate2.receipt(), duplicate3.receipt());
    }

    private List<TransactionReceipt> getExpectedChildReceiptList() {
        return List.of(recordOne.receipt(), recordTwo.receipt(), recordThree.receipt());
    }

    private Query createGetTransactionReceiptQuery(
            final TransactionID transactionID, final boolean includeDuplicates, final boolean includeChildReceipts) {
        final var data = TransactionGetReceiptQuery.newBuilder()
                .transactionID(transactionID)
                .includeDuplicates(includeDuplicates)
                .includeChildReceipts(includeChildReceipts)
                .header(QueryHeader.newBuilder().build())
                .build();

        return Query.newBuilder().transactionGetReceipt(data).build();
    }

    private Query createEmptysQuery() {
        final var data = TransactionGetReceiptQuery.newBuilder()
                .header(QueryHeader.newBuilder().build())
                .build();

        return Query.newBuilder().transactionGetReceipt(data).build();
    }
}
