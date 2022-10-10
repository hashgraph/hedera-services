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
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.ActiveVersions;
import com.hedera.services.context.properties.SemanticVersions;
import com.hederahashgraph.api.proto.java.NetworkGetVersionInfoQuery;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import com.hederahashgraph.api.proto.java.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GetVersionInfoAnswerTest {
    private static final String node = "0.0.3";
    private static final long fee = 1_234L;
    private static final String payer = "0.0.12345";
    private static final SemanticVersion expectedVersions =
            SemanticVersion.newBuilder().setMajor(0).setMinor(4).setPatch(0).build();

    private Transaction paymentTxn;
    private StateView view;

    private SemanticVersions semanticVersions;
    private GetVersionInfoAnswer subject;

    @BeforeEach
    void setup() {
        view = mock(StateView.class);
        semanticVersions = mock(SemanticVersions.class);
        given(semanticVersions.getDeployed())
                .willReturn(new ActiveVersions(expectedVersions, expectedVersions));

        subject = new GetVersionInfoAnswer(semanticVersions);
    }

    @Test
    void getsInvalidResponse() throws Throwable {
        final var query = validQuery(COST_ANSWER, fee);

        final var response = subject.responseGiven(query, view, FAIL_INVALID, fee);

        assertTrue(response.hasNetworkGetVersionInfo());
        assertEquals(
                FAIL_INVALID,
                response.getNetworkGetVersionInfo().getHeader().getNodeTransactionPrecheckCode());
        assertEquals(
                COST_ANSWER, response.getNetworkGetVersionInfo().getHeader().getResponseType());
        assertEquals(fee, response.getNetworkGetVersionInfo().getHeader().getCost());
    }

    @Test
    void getsCostAnswerResponse() throws Throwable {
        final var query = validQuery(COST_ANSWER, fee);

        final var response = subject.responseGiven(query, view, OK, fee);

        assertTrue(response.hasNetworkGetVersionInfo());
        final var opResponse = response.getNetworkGetVersionInfo();
        assertEquals(OK, opResponse.getHeader().getNodeTransactionPrecheckCode());
        assertEquals(COST_ANSWER, opResponse.getHeader().getResponseType());
        assertEquals(fee, opResponse.getHeader().getCost());
    }

    @Test
    void getsVersionInfoWhenAvailable() throws Throwable {
        final var sensibleQuery = validQuery(ANSWER_ONLY, 5L);
        assertEquals(OK, subject.checkValidity(sensibleQuery, view));

        final var response = subject.responseGiven(sensibleQuery, view, OK, 0L);

        final var opResponse = response.getNetworkGetVersionInfo();
        assertTrue(opResponse.hasHeader(), "Missing response header!");
        assertEquals(OK, opResponse.getHeader().getNodeTransactionPrecheckCode());
        assertEquals(expectedVersions, opResponse.getHederaServicesVersion());
        assertEquals(expectedVersions, opResponse.getHapiProtoVersion());
    }

    @Test
    void respectsMetaValidity() throws Throwable {
        final var sensibleQuery = validQuery(ANSWER_ONLY, 5L);

        final var response = subject.responseGiven(sensibleQuery, view, INVALID_TRANSACTION, 0L);

        final var opResponse = response.getNetworkGetVersionInfo();
        assertEquals(INVALID_TRANSACTION, opResponse.getHeader().getNodeTransactionPrecheckCode());
    }

    private Query validQuery(final ResponseType type, final long payment) throws Throwable {
        this.paymentTxn = payerSponsoredTransfer(payer, COMPLEX_KEY_ACCOUNT_KT, node, payment);

        final var header =
                QueryHeader.newBuilder().setPayment(this.paymentTxn).setResponseType(type);
        final var op = NetworkGetVersionInfoQuery.newBuilder().setHeader(header);
        return Query.newBuilder().setNetworkGetVersionInfo(op).build();
    }
}
