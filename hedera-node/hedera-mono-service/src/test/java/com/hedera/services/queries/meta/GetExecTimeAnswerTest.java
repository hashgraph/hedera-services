/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.queries.meta;

import static com.hedera.test.factories.scenarios.TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT;
import static com.hedera.test.utils.TxnUtils.payerSponsoredTransfer;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.stats.ExecutionTimeTracker;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.NetworkGetExecutionTimeQuery;
import com.hederahashgraph.api.proto.java.NetworkGetExecutionTimeResponse;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetExecTimeAnswerTest {
    private final String node = "0.0.3";
    private final long fee = 1_234L;
    private final long nanos = 1_234_567L;
    private final String payer = "0.0.12345";

    private Transaction paymentTxn;

    @Mock private StateView view;
    @Mock private ExecutionTimeTracker executionTimeTracker;

    private GetExecTimeAnswer subject;

    @BeforeEach
    void setUp() {
        subject = new GetExecTimeAnswer(executionTimeTracker);
    }

    @Test
    void helpersWork() throws Throwable {
        Query query = validQuery(ANSWER_ONLY, 5L);
        Response response =
                Response.newBuilder()
                        .setNetworkGetExecutionTime(
                                NetworkGetExecutionTimeResponse.newBuilder()
                                        .setHeader(
                                                ResponseHeader.newBuilder()
                                                        .setNodeTransactionPrecheckCode(
                                                                INVALID_TRANSACTION_ID)))
                        .build();

        final var payment = subject.extractPaymentFrom(query);
        assertTrue(payment.isPresent());
        assertFalse(subject.needsAnswerOnlyCost(query));
        assertEquals(INVALID_TRANSACTION_ID, subject.extractValidityFrom(response));
    }

    @Test
    void getsInvalidResponse() throws Throwable {
        // setup:
        Query query = validQuery(COST_ANSWER, fee);

        // when:
        Response response = subject.responseGiven(query, view, FAIL_INVALID, fee);

        // then:
        assertTrue(response.hasNetworkGetExecutionTime());
        assertEquals(
                FAIL_INVALID,
                response.getNetworkGetExecutionTime().getHeader().getNodeTransactionPrecheckCode());
        assertEquals(
                COST_ANSWER, response.getNetworkGetExecutionTime().getHeader().getResponseType());
        assertEquals(fee, response.getNetworkGetExecutionTime().getHeader().getCost());
    }

    @Test
    void getsCostAnswerResponse() throws Throwable {
        // setup:
        Query query = validQuery(COST_ANSWER, fee);

        // when:
        Response response = subject.responseGiven(query, view, OK, fee);

        // then:
        assertTrue(response.hasNetworkGetExecutionTime());
        NetworkGetExecutionTimeResponse opResponse = response.getNetworkGetExecutionTime();
        assertEquals(OK, opResponse.getHeader().getNodeTransactionPrecheckCode());
        assertEquals(COST_ANSWER, opResponse.getHeader().getResponseType());
        assertEquals(fee, opResponse.getHeader().getCost());
        assertEquals(OK, subject.checkValidity(query, view));
    }

    @Test
    void complainsWhenAnyExecutionTimeUnavailable() throws Throwable {
        Query sensibleQuery = validQuery(ANSWER_ONLY, 5L, aTxnId, bTxnId);
        given(executionTimeTracker.getExecNanosIfPresentFor(bTxnId)).willReturn(null);

        Response response = subject.responseGiven(sensibleQuery, view, OK, 0L);

        final var opResponse = response.getNetworkGetExecutionTime();
        assertEquals(
                INVALID_TRANSACTION_ID, opResponse.getHeader().getNodeTransactionPrecheckCode());
    }

    @Test
    void getsExecutionTimeWhenAvailable() throws Throwable {
        // setup:
        Query sensibleQuery = validQuery(ANSWER_ONLY, 5L, aTxnId, bTxnId, cTxnId);

        // given:
        assertEquals(OK, subject.checkValidity(sensibleQuery, view));

        // when:
        Response response = subject.responseGiven(sensibleQuery, view, OK, 0L);

        // then:
        var opResponse = response.getNetworkGetExecutionTime();
        assertTrue(opResponse.hasHeader(), "Missing response header!");
        assertEquals(OK, opResponse.getHeader().getNodeTransactionPrecheckCode());
        assertEquals(List.of(nanos, nanos, nanos), opResponse.getExecutionTimesList());
    }

    @Test
    void respectsMetaValidity() throws Throwable {
        // given:
        Query sensibleQuery = validQuery(ANSWER_ONLY, 5L);

        // when:
        Response response = subject.responseGiven(sensibleQuery, view, INVALID_TRANSACTION, 0L);

        // then:
        var opResponse = response.getNetworkGetExecutionTime();
        assertEquals(INVALID_TRANSACTION, opResponse.getHeader().getNodeTransactionPrecheckCode());
    }

    private Query validQuery(ResponseType type, long payment, TransactionID... txnIds)
            throws Throwable {
        this.paymentTxn = payerSponsoredTransfer(payer, COMPLEX_KEY_ACCOUNT_KT, node, payment);

        QueryHeader.Builder header =
                QueryHeader.newBuilder().setPayment(this.paymentTxn).setResponseType(type);
        NetworkGetExecutionTimeQuery.Builder op =
                NetworkGetExecutionTimeQuery.newBuilder()
                        .setHeader(header)
                        .addAllTransactionIds(List.of(txnIds));

        for (var txnId : txnIds) {
            given(executionTimeTracker.getExecNanosIfPresentFor(txnId)).willReturn(nanos);
        }

        return Query.newBuilder().setNetworkGetExecutionTime(op).build();
    }

    private final TransactionID aTxnId =
            TransactionID.newBuilder()
                    .setTransactionValidStart(Timestamp.newBuilder().setSeconds(1_234_567L))
                    .setAccountID(IdUtils.asAccount("0.0.2"))
                    .build();
    private final TransactionID bTxnId =
            TransactionID.newBuilder()
                    .setTransactionValidStart(Timestamp.newBuilder().setSeconds(1_234_567L))
                    .setAccountID(IdUtils.asAccount("0.0.3"))
                    .build();
    private final TransactionID cTxnId =
            TransactionID.newBuilder()
                    .setTransactionValidStart(Timestamp.newBuilder().setSeconds(1_234_567L))
                    .setAccountID(IdUtils.asAccount("0.0.4"))
                    .build();
}
