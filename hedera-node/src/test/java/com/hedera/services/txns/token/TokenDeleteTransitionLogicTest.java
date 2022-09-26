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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.never;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenDeleteTransitionLogicTest {
    private static final TokenID grpcTokenId = IdUtils.asToken("0.0.12345");
    private static final Id tokenId = Id.fromGrpcToken(grpcTokenId);
    private TransactionContext txnCtx;
    private SignedTxnAccessor accessor;
    private TypedTokenStore typedTokenStore;
    private AccountStore accountStore;
    private Token token;
    private final Account treasury = new Account(new Id(0, 0, 1234));

    private TransactionBody tokenDeleteTxn;
    private SigImpactHistorian sigImpactHistorian;
    private TokenDeleteTransitionLogic subject;

    @BeforeEach
    void setup() {
        txnCtx = mock(TransactionContext.class);
        accessor = mock(SignedTxnAccessor.class);
        typedTokenStore = mock(TypedTokenStore.class);
        accountStore = mock(AccountStore.class);
        sigImpactHistorian = mock(SigImpactHistorian.class);
        token = mock(Token.class);
        DeleteLogic deleteLogic =
                new DeleteLogic(accountStore, typedTokenStore, sigImpactHistorian);
        subject = new TokenDeleteTransitionLogic(txnCtx, deleteLogic);
    }

    @Test
    void followsHappyPath() {
        givenValidTxnCtx();
        given(typedTokenStore.loadToken(tokenId)).willReturn(token);
        given(token.hasAdminKey()).willReturn(true);
        given(token.isDeleted()).willReturn(false);
        given(token.isBelievedToHaveBeenAutoRemoved()).willReturn(false);
        given(token.getTreasury()).willReturn(treasury);

        subject.doStateTransition();

        verify(token).delete();
        verify(typedTokenStore).commitToken(token);
        verify(accountStore).commitAccount(treasury);
        verify(sigImpactHistorian).markEntityChanged(tokenId.num());
    }

    @Test
    void capturesInvalidDelete() {
        givenValidTxnCtx();
        given(typedTokenStore.loadToken(tokenId))
                .willThrow(new InvalidTransactionException(INVALID_TOKEN_ID));

        assertFailsWith(() -> subject.doStateTransition(), INVALID_TOKEN_ID);

        verify(token, never()).delete();
        verify(typedTokenStore, never()).commitToken(token);
    }

    private void assertFailsWith(final Runnable something, final ResponseCodeEnum status) {
        final var ex = assertThrows(InvalidTransactionException.class, something::run);
        assertEquals(status, ex.getResponseCode());
    }

    @Test
    void capturesInvalidDeletionDueToAlreadyDeleted() {
        givenValidTxnCtx();
        given(typedTokenStore.loadToken(tokenId))
                .willThrow(new InvalidTransactionException(TOKEN_WAS_DELETED));

        assertFailsWith(() -> subject.doStateTransition(), TOKEN_WAS_DELETED);

        verify(token, never()).delete();
        verify(typedTokenStore, never()).commitToken(token);
    }

    @Test
    void hasCorrectApplicability() {
        givenValidTxnCtx();

        assertTrue(subject.applicability().test(tokenDeleteTxn));
        assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
    }

    @Test
    void acceptsValidTxn() {
        givenValidTxnCtx();

        assertEquals(OK, subject.semanticCheck().apply(tokenDeleteTxn));
    }

    @Test
    void rejectsMissingToken() {
        givenMissingToken();

        assertEquals(INVALID_TOKEN_ID, subject.semanticCheck().apply(tokenDeleteTxn));
    }

    private void givenValidTxnCtx() {
        tokenDeleteTxn =
                TransactionBody.newBuilder()
                        .setTokenDeletion(
                                TokenDeleteTransactionBody.newBuilder().setToken(grpcTokenId))
                        .build();
        given(accessor.getTxn()).willReturn(tokenDeleteTxn);
        given(txnCtx.accessor()).willReturn(accessor);
        given(typedTokenStore.loadToken(tokenId)).willReturn(token);
    }

    private void givenMissingToken() {
        tokenDeleteTxn =
                TransactionBody.newBuilder()
                        .setTokenDeletion(TokenDeleteTransactionBody.newBuilder())
                        .build();
    }
}
