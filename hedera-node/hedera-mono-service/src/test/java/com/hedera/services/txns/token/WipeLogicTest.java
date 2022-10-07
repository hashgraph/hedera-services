/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BATCH_SIZE_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.OwnershipTracker;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenWipeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WipeLogicTest {
    private final AccountID accountID = IdUtils.asAccount("1.2.4");
    private final TokenID id = IdUtils.asToken("1.2.3");
    private final Id idOfToken = new Id(1, 2, 3);
    private final Id idOfAccount = new Id(1, 2, 4);
    private final long wipeAmount = 100;

    private TransactionBody tokenWipeTxn;
    private Account account;

    @Mock private Token token;
    @Mock private TypedTokenStore typedTokenStore;
    @Mock private AccountStore accountStore;
    @Mock private GlobalDynamicProperties dynamicProperties;

    private WipeLogic subject;

    @BeforeEach
    void setup() {
        subject = new WipeLogic(typedTokenStore, accountStore, dynamicProperties);
    }

    @Test
    void followsHappyPathForCommon() {
        givenValidCommonTxnCtx();

        // when:
        subject.wipe(idOfToken, idOfAccount, wipeAmount, anyList());

        // then:
        verify(token).wipe(any(), anyLong());
        verify(typedTokenStore).commitToken(token);
    }

    @Test
    void followsHappyPathForUnique() {
        givenValidUniqueTxnCtx();
        // needed only in the context of this test
        Account acc = mock(Account.class);
        TokenRelationship accRel = mock(TokenRelationship.class);
        given(accountStore.loadAccount(any())).willReturn(acc);
        given(typedTokenStore.loadTokenRelationship(token, acc)).willReturn(accRel);

        // when:
        subject.wipe(idOfToken, idOfAccount, wipeAmount, List.of(1L, 2L, 3L));

        // then:
        verify(token).wipe(any(OwnershipTracker.class), any(TokenRelationship.class), anyList());
        verify(token).getType();
        verify(typedTokenStore)
                .loadUniqueTokens(token, tokenWipeTxn.getTokenWipe().getSerialNumbersList());
        verify(typedTokenStore).commitToken(token);
        verify(typedTokenStore).commitTrackers(any(OwnershipTracker.class));
        verify(accountStore).commitAccount(any(Account.class));
    }

    @Test
    void validatesSyntax() {
        tokenWipeTxn =
                TransactionBody.newBuilder()
                        .setTokenWipe(
                                TokenWipeAccountTransactionBody.newBuilder()
                                        .setToken(id)
                                        .setAccount(accountID)
                                        .addAllSerialNumbers(List.of(1L, 2L, 3L)))
                        .build();

        given(dynamicProperties.areNftsEnabled()).willReturn(true);
        given(dynamicProperties.maxBatchSizeWipe()).willReturn(10);

        assertEquals(OK, subject.validateSyntax(tokenWipeTxn));
    }

    @Test
    void validatesSyntaxError() {
        tokenWipeTxn =
                TransactionBody.newBuilder()
                        .setTokenWipe(
                                TokenWipeAccountTransactionBody.newBuilder()
                                        .setToken(id)
                                        .setAccount(accountID)
                                        .addAllSerialNumbers(List.of(1L, 2L, 3L)))
                        .build();

        given(dynamicProperties.areNftsEnabled()).willReturn(true);
        given(dynamicProperties.maxBatchSizeWipe()).willReturn(1);

        assertEquals(BATCH_SIZE_LIMIT_EXCEEDED, subject.validateSyntax(tokenWipeTxn));
    }

    private void givenValidCommonTxnCtx() {
        tokenWipeTxn =
                TransactionBody.newBuilder()
                        .setTokenWipe(
                                TokenWipeAccountTransactionBody.newBuilder()
                                        .setToken(id)
                                        .setAccount(accountID)
                                        .setAmount(wipeAmount))
                        .build();
        given(typedTokenStore.loadToken(any())).willReturn(token);
        given(token.getType()).willReturn(TokenType.FUNGIBLE_COMMON);
        given(accountStore.loadAccount(any())).willReturn(account);
        given(typedTokenStore.loadTokenRelationship(token, account))
                .willReturn(new TokenRelationship(token, account));
    }

    private void givenValidUniqueTxnCtx() {
        tokenWipeTxn =
                TransactionBody.newBuilder()
                        .setTokenWipe(
                                TokenWipeAccountTransactionBody.newBuilder()
                                        .setToken(id)
                                        .setAccount(accountID)
                                        .addAllSerialNumbers(List.of(1L, 2L, 3L)))
                        .build();
        given(typedTokenStore.loadToken(any())).willReturn(token);
        given(token.getType()).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);
    }
}
