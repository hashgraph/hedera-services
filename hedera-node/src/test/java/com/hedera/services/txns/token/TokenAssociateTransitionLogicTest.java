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
package com.hedera.services.txns.token;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ID_REPEATED_IN_TOKEN_LIST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.state.validation.UsageLimits;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenAssociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenAssociateTransitionLogicTest {
    private final AccountID account = IdUtils.asAccount("0.0.2");
    private final TokenID firstToken = IdUtils.asToken("1.2.3");
    private final TokenID secondToken = IdUtils.asToken("2.3.4");
    private TransactionBody tokenAssociateTxn;

    private AssociateLogic associateLogic;
    private TokenAssociateTransitionLogic subject;

    @Mock private UsageLimits usageLimits;
    @Mock private TypedTokenStore tokenStore;
    @Mock private AccountStore accountStore;
    @Mock private TransactionContext txnCtx;
    @Mock private TxnAccessor accessor;
    @Mock private GlobalDynamicProperties dynamicProperties;

    @BeforeEach
    void setup() {
        associateLogic =
                new AssociateLogic(usageLimits, tokenStore, accountStore, dynamicProperties);
        subject = new TokenAssociateTransitionLogic(txnCtx, associateLogic);
    }

    @Test
    void hasCorrectApplicability() {
        givenValidTxn();

        // expect:
        assertTrue(subject.applicability().test(tokenAssociateTxn));
        assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
    }

    @Test
    void happyPathWorks() {
        givenValidTxn();
        given(txnCtx.accessor()).willReturn(accessor);
        given(accessor.getTxn()).willReturn(tokenAssociateTxn);
        associateLogic = mock(AssociateLogic.class);
        subject = new TokenAssociateTransitionLogic(txnCtx, associateLogic);

        subject.doStateTransition();

        verify(associateLogic)
                .associate(Id.fromGrpcAccount(account), List.of(firstToken, secondToken));
    }

    @Test
    void acceptsValidTxn() {
        givenValidTxn();

        // expect:
        assertEquals(OK, subject.semanticCheck().apply(tokenAssociateTxn));
    }

    @Test
    void rejectsMissingAccount() {
        givenMissingAccount();

        // expect:
        assertEquals(INVALID_ACCOUNT_ID, subject.semanticCheck().apply(tokenAssociateTxn));
    }

    @Test
    void rejectsDuplicateTokens() {
        givenDuplicateTokens();

        // expect:
        assertEquals(
                TOKEN_ID_REPEATED_IN_TOKEN_LIST, subject.semanticCheck().apply(tokenAssociateTxn));
    }

    private void givenValidTxn() {
        tokenAssociateTxn =
                TransactionBody.newBuilder()
                        .setTokenAssociate(
                                TokenAssociateTransactionBody.newBuilder()
                                        .setAccount(account)
                                        .addAllTokens(List.of(firstToken, secondToken)))
                        .build();
    }

    private void givenMissingAccount() {
        tokenAssociateTxn =
                TransactionBody.newBuilder()
                        .setTokenAssociate(TokenAssociateTransactionBody.newBuilder())
                        .build();
    }

    private void givenDuplicateTokens() {
        tokenAssociateTxn =
                TransactionBody.newBuilder()
                        .setTokenAssociate(
                                TokenAssociateTransactionBody.newBuilder()
                                        .setAccount(account)
                                        .addTokens(firstToken)
                                        .addTokens(firstToken))
                        .build();
    }
}
