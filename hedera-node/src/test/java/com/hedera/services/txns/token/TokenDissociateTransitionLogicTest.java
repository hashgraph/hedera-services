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
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.token.process.DissociationFactory;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenDissociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenDissociateTransitionLogicTest {
    private final AccountID targetAccount = IdUtils.asAccount("1.2.3");
    private final TokenID firstTargetToken = IdUtils.asToken("2.3.4");

    @Mock private TransactionContext txnCtx;
    @Mock private SignedTxnAccessor accessor;
    @Mock private OptionValidator validator;
    @Mock private TypedTokenStore tokenStore;
    @Mock private AccountStore accountStore;
    @Mock private DissociationFactory dissociationFactory;

    private DissociateLogic dissociateLogic;
    private TokenDissociateTransitionLogic subject;

    @BeforeEach
    void setUp() {
        dissociateLogic =
                new DissociateLogic(validator, tokenStore, accountStore, dissociationFactory);
        subject = new TokenDissociateTransitionLogic(txnCtx, dissociateLogic);
    }

    @Test
    void oksValidTxn() {
        // expect:
        assertEquals(OK, subject.semanticCheck().apply(validDissociateTxn()));
    }

    @Test
    void hasCorrectApplicability() {
        // expect:
        assertTrue(subject.applicability().test(validDissociateTxn()));
        assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
    }

    @Test
    void rejectsMissingAccountId() {
        // given:
        final var check = subject.semanticCheck();

        // expect:
        assertEquals(INVALID_ACCOUNT_ID, check.apply(dissociateTxnWith(missingAccountIdOp())));
    }

    @Test
    void rejectsRepatedTokenId() {
        // given:
        final var check = subject.semanticCheck();

        // expect:
        assertEquals(
                TOKEN_ID_REPEATED_IN_TOKEN_LIST,
                check.apply(dissociateTxnWith(repeatedTokenIdOp())));
    }

    @Test
    void callsDissociateLogicWithCorrectParams() {
        final var accountId = new Id(1, 2, 3);
        dissociateLogic = mock(DissociateLogic.class);
        subject = new TokenDissociateTransitionLogic(txnCtx, dissociateLogic);

        given(accessor.getTxn()).willReturn(validDissociateTxn());
        given(txnCtx.accessor()).willReturn(accessor);

        subject.doStateTransition();

        verify(dissociateLogic)
                .dissociate(
                        accountId, txnCtx.accessor().getTxn().getTokenDissociate().getTokensList());
    }

    private TransactionBody validDissociateTxn() {
        return TransactionBody.newBuilder().setTokenDissociate(validOp()).build();
    }

    private TransactionBody dissociateTxnWith(TokenDissociateTransactionBody op) {
        return TransactionBody.newBuilder().setTokenDissociate(op).build();
    }

    private TokenDissociateTransactionBody validOp() {
        return TokenDissociateTransactionBody.newBuilder()
                .setAccount(targetAccount)
                .addTokens(firstTargetToken)
                .build();
    }

    private TokenDissociateTransactionBody missingAccountIdOp() {
        return TokenDissociateTransactionBody.newBuilder().addTokens(firstTargetToken).build();
    }

    private TokenDissociateTransactionBody repeatedTokenIdOp() {
        return TokenDissociateTransactionBody.newBuilder()
                .setAccount(targetAccount)
                .addTokens(firstTargetToken)
                .addTokens(firstTargetToken)
                .build();
    }
}
