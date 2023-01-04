/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.txns.token;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.store.AccountStore;
import com.hedera.node.app.service.mono.store.TypedTokenStore;
import com.hedera.node.app.service.mono.store.models.Account;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hedera.node.app.service.mono.store.models.OwnershipTracker;
import com.hedera.node.app.service.mono.store.models.Token;
import com.hedera.node.app.service.mono.store.models.TokenRelationship;
import com.hedera.node.app.service.mono.txns.validation.OptionValidator;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.TokenBurnTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BurnLogicTest {
    private final long amount = 123L;
    private final TokenID grpcId = IdUtils.asToken("1.2.3");
    private TokenRelationship treasuryRel;
    private final Id id = new Id(1, 2, 3);
    private final Id treasuryId = new Id(2, 4, 6);
    private final Account treasury = new Account(treasuryId);

    @Mock private Token token;
    @Mock private TypedTokenStore store;
    @Mock private AccountStore accountStore;
    @Mock private OptionValidator validator;
    @Mock private GlobalDynamicProperties dynamicProperties;

    private TransactionBody tokenBurnTxn;

    private BurnLogic subject;

    @BeforeEach
    void setup() {
        subject = new BurnLogic(validator, store, accountStore, dynamicProperties);
    }

    @Test
    void followsHappyPathForCommon() {
        // setup:
        treasuryRel = new TokenRelationship(token, treasury);

        givenValidTxnCtx();
        given(store.loadToken(id)).willReturn(token);
        given(token.getTreasury()).willReturn(treasury);
        given(store.loadTokenRelationship(token, treasury)).willReturn(treasuryRel);
        given(token.getType()).willReturn(TokenType.FUNGIBLE_COMMON);
        // when:
        subject.burn(id, amount, anyList());

        // then:
        verify(token).burn(treasuryRel, amount);
        verify(store).commitToken(token);
        verify(store).commitTokenRelationships(List.of(treasuryRel));
    }

    @Test
    void followsHappyPathForUnique() {
        // setup:
        treasuryRel = new TokenRelationship(token, treasury);

        givenValidUniqueTxnCtx();
        given(store.loadToken(id)).willReturn(token);
        given(token.getTreasury()).willReturn(treasury);
        given(store.loadTokenRelationship(token, treasury)).willReturn(treasuryRel);
        given(token.getType()).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);
        // when:
        subject.burn(id, amount, tokenBurnTxn.getTokenBurn().getSerialNumbersList());

        // then:
        verify(token).getType();
        verify(store).loadUniqueTokens(token, tokenBurnTxn.getTokenBurn().getSerialNumbersList());
        verify(token).burn(any(OwnershipTracker.class), eq(treasuryRel), any(List.class));
        verify(store).commitToken(token);
        verify(store).commitTokenRelationships(List.of(treasuryRel));
        verify(store).commitTrackers(any(OwnershipTracker.class));
        verify(accountStore).commitAccount(any(Account.class));
    }

    @Test
    void precheckWorksForZeroFungibleAmount() {
        givenValidTxnCtxWithZeroAmount();
        assertEquals(OK, subject.validateSyntax(tokenBurnTxn));
    }

    @Test
    void precheckWorksForNonZeroFungibleAmount() {
        givenUniqueTxnCtxWithNoSerials();
        assertEquals(OK, subject.validateSyntax(tokenBurnTxn));
    }

    private void givenValidTxnCtx() {
        tokenBurnTxn =
                TransactionBody.newBuilder()
                        .setTokenBurn(
                                TokenBurnTransactionBody.newBuilder()
                                        .setToken(grpcId)
                                        .setAmount(amount))
                        .build();
    }

    private void givenValidTxnCtxWithZeroAmount() {
        tokenBurnTxn =
                TransactionBody.newBuilder()
                        .setTokenBurn(
                                TokenBurnTransactionBody.newBuilder().setToken(grpcId).setAmount(0))
                        .build();
    }

    private void givenUniqueTxnCtxWithNoSerials() {
        tokenBurnTxn =
                TransactionBody.newBuilder()
                        .setTokenBurn(
                                TokenBurnTransactionBody.newBuilder()
                                        .setToken(grpcId)
                                        .addAllSerialNumbers(List.of()))
                        .build();
    }

    private void givenValidUniqueTxnCtx() {
        tokenBurnTxn =
                TransactionBody.newBuilder()
                        .setTokenBurn(
                                TokenBurnTransactionBody.newBuilder()
                                        .setToken(grpcId)
                                        .addAllSerialNumbers(List.of(1L)))
                        .build();
    }
}
