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


import static com.hedera.test.factories.scenarios.TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT;
import static com.hedera.test.utils.TxnUtils.payerSponsoredPbjTransfer;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.QueryHeader;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.ResponseHeader;
import com.hedera.hapi.node.base.ResponseType;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.token.AccountDetails;
import com.hedera.hapi.node.token.GetAccountDetailsQuery;
import com.hedera.hapi.node.token.GetAccountDetailsResponse;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.Response;
import com.hedera.node.app.service.networkadmin.impl.handlers.NetworkGetAccountDetailsHandler;
import com.hedera.node.app.service.networkadmin.impl.utils.NetworkAdminServiceUtil;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.node.app.spi.workflows.QueryContext;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NetworkGetAccountDetailsHandlerTest extends NetworkAdminHandlerTestBase {

    @Mock
    private NetworkInfo networkInfo;

    @Mock
    private QueryContext context;

    private NetworkGetAccountDetailsHandler networkGetAccountDetailsHandler;

    @BeforeEach
    void setUp() {
        networkGetAccountDetailsHandler = new NetworkGetAccountDetailsHandler();
        final var configuration = new HederaTestConfigBuilder().getOrCreateConfig();
        lenient().when(context.configuration()).thenReturn(configuration);
    }

    @Test
    void extractsHeader() {
        final var query = createGetAccountDetailsQuery(accountNum);
        final var header = networkGetAccountDetailsHandler.extractHeader(query);
        final var op = query.accountDetailsOrThrow();
        assertEquals(op.header(), header);
    }

    @Test
    void createsEmptyResponse() {
        final var responseHeader = ResponseHeader.newBuilder()
                .nodeTransactionPrecheckCode(ResponseCodeEnum.FAIL_FEE)
                .build();
        final var response = networkGetAccountDetailsHandler.createEmptyResponse(responseHeader);
        final var expectedResponse = Response.newBuilder()
                .accountDetails(GetAccountDetailsResponse.newBuilder().header(responseHeader))
                .build();
        assertEquals(expectedResponse, response);
    }

    @Test
    void requiresPayment() {
        assertTrue(networkGetAccountDetailsHandler.requiresNodePayment(ResponseType.ANSWER_ONLY));
        assertTrue(networkGetAccountDetailsHandler.requiresNodePayment(ResponseType.ANSWER_STATE_PROOF));
        assertFalse(networkGetAccountDetailsHandler.requiresNodePayment(ResponseType.COST_ANSWER));
        assertFalse(networkGetAccountDetailsHandler.requiresNodePayment(ResponseType.COST_ANSWER_STATE_PROOF));
    }

    @Test
    void needsAnswerOnlyCostForCostAnswer() {
        assertFalse(networkGetAccountDetailsHandler.needsAnswerOnlyCost(ResponseType.ANSWER_ONLY));
        assertFalse(networkGetAccountDetailsHandler.needsAnswerOnlyCost(ResponseType.ANSWER_STATE_PROOF));
        assertTrue(networkGetAccountDetailsHandler.needsAnswerOnlyCost(ResponseType.COST_ANSWER));
        assertFalse(networkGetAccountDetailsHandler.needsAnswerOnlyCost(ResponseType.COST_ANSWER_STATE_PROOF));
    }

    @Test
    void validatesQueryWhenValidAccount() throws Throwable {

        final var query = createGetAccountDetailsQuery(accountNum);
        given(context.query()).willReturn(query);
        given(context.createStore(ReadableAccountStore.class)).willReturn(readableAccountStore);

        assertThatCode(() -> networkGetAccountDetailsHandler.validate(context)).doesNotThrowAnyException();
    }

    @Test
    void getsResponseIfFailedResponse() {
        final var responseHeader = ResponseHeader.newBuilder()
                .nodeTransactionPrecheckCode(ResponseCodeEnum.FAIL_FEE)
                .build();

        final var query = createGetAccountDetailsQuery(accountNum);
        when(context.query()).thenReturn(query);
        when(context.createStore(ReadableAccountStore.class)).thenReturn(readableAccountStore);

        final var response = networkGetAccountDetailsHandler.findResponse(context, responseHeader);
        final var op = response.accountDetailsOrThrow();
        assertEquals(ResponseCodeEnum.FAIL_FEE, op.header().nodeTransactionPrecheckCode());
        assertNull(op.accountDetails());
    }

    @Test
    void getsResponseIsEmptyWhenAccountNotExist() {
        givenValidAccount(true);
        refreshStoresWithEntitiesOnlyInReadable();
        final var responseHeader = ResponseHeader.newBuilder()
                .nodeTransactionPrecheckCode(ResponseCodeEnum.OK)
                .build();

        final var query = createGetAccountDetailsQuery(567L);
        when(context.query()).thenReturn(query);
        when(context.createStore(ReadableAccountStore.class)).thenReturn(readableAccountStore);
        when(context.createStore(ReadableTokenStore.class)).thenReturn(readableTokenStore);
        when(context.createStore(ReadableTokenRelationStore.class)).thenReturn(readableTokenRelStore);

        final var response = networkGetAccountDetailsHandler.findResponse(context, responseHeader);
        final var accountDetailsResponse = response.accountDetailsOrThrow();
        assertEquals(ResponseCodeEnum.OK, accountDetailsResponse.header().nodeTransactionPrecheckCode());
        assertEquals(null, accountDetailsResponse.accountDetails());
    }

    @Test
    void getsResponseIfFileDeletedOkResponse() {
        givenValidAccount(true);
        refreshStoresWithEntitiesOnlyInReadable();
        final var responseHeader = ResponseHeader.newBuilder()
                .nodeTransactionPrecheckCode(ResponseCodeEnum.OK)
                .build();
        final var expectedInfo = getExpectedInfo(true);

        final var query = createGetAccountDetailsQuery(accountNum);
        when(context.query()).thenReturn(query);
        when(context.createStore(ReadableAccountStore.class)).thenReturn(readableAccountStore);
        when(context.createStore(ReadableTokenStore.class)).thenReturn(readableTokenStore);
        when(context.createStore(ReadableTokenRelationStore.class)).thenReturn(readableTokenRelStore);

        final var response = networkGetAccountDetailsHandler.findResponse(context, responseHeader);
        final var accountDetailsResponse = response.accountDetailsOrThrow();
        assertEquals(ResponseCodeEnum.OK, accountDetailsResponse.header().nodeTransactionPrecheckCode());
        assertEquals(expectedInfo, accountDetailsResponse.accountDetails());
    }

    @Test
    void getsResponseIfOkResponse() {
        final var responseHeader = ResponseHeader.newBuilder()
                .nodeTransactionPrecheckCode(ResponseCodeEnum.OK)
                .build();
        final var expectedInfo = getExpectedInfo(false);

        final var query = createGetAccountDetailsQuery(accountNum);
        when(context.query()).thenReturn(query);
        when(context.createStore(ReadableAccountStore.class)).thenReturn(readableAccountStore);
        when(context.createStore(ReadableTokenStore.class)).thenReturn(readableTokenStore);
        when(context.createStore(ReadableTokenRelationStore.class)).thenReturn(readableTokenRelStore);

        final var response = networkGetAccountDetailsHandler.findResponse(context, responseHeader);
        final var accountDetailsResponse = response.accountDetailsOrThrow();
        assertEquals(ResponseCodeEnum.OK, accountDetailsResponse.header().nodeTransactionPrecheckCode());
        assertEquals(expectedInfo, accountDetailsResponse.accountDetails());
    }

    private AccountDetails getExpectedInfo(boolean deleted) {
        return AccountDetails.newBuilder()
                .accountId(AccountID.newBuilder().accountNum(accountNum).build())
                .contractAccountId(NetworkAdminServiceUtil.asHexedEvmAddress(AccountID.newBuilder().accountNum(accountNum).build()))
                .deleted(deleted)
                .key(key)
                .balance(payerBalance)
                .receiverSigRequired(true)
                .expirationTime(Timestamp.newBuilder().seconds(1_234_567L).build())
                .autoRenewPeriod(Duration.newBuilder().seconds(72000).build())
                .memo("testAccount")
                .ownedNfts(2)
                .maxAutomaticTokenAssociations(10)
                .alias(alias.alias())
                .ledgerId(ledgerId)
                .grantedCryptoAllowances(Collections.emptyList())
                .grantedNftAllowances(Collections.emptyList())
                .grantedTokenAllowances(Collections.emptyList())
                .tokenRelationships(Collections.emptyList())
                .build();
    }

    private Query createGetAccountDetailsQuery(final long accountNum) {
        final var payment =
                payerSponsoredPbjTransfer(payerIdLiteral, COMPLEX_KEY_ACCOUNT_KT, beneficiaryIdStr, paymentAmount);
        final var data = GetAccountDetailsQuery.newBuilder()
                .accountId(AccountID.newBuilder().accountNum(accountNum).build())
                .header(QueryHeader.newBuilder().payment(payment).build())
                .build();

        return Query.newBuilder().accountDetails(data).build();
    }
}
