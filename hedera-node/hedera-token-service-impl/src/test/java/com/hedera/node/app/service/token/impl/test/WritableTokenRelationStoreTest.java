/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.test;

import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static com.hedera.node.app.service.token.impl.handlers.BaseTokenHandler.asToken;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.mock;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.spi.WritableKVStateBase;
import com.swirlds.state.spi.WritableStates;
import java.util.Set;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WritableTokenRelationStoreTest {
    private static final long TOKEN_10 = 10L;
    private static final TokenID TOKEN_10_ID =
            TokenID.newBuilder().tokenNum(TOKEN_10).build();
    private static final long ACCOUNT_20 = 20L;
    private static final AccountID ACCOUNT_20_ID =
            AccountID.newBuilder().accountNum(ACCOUNT_20).build();

    private static final Configuration CONFIGURATION = HederaTestConfigBuilder.createConfig();

    @Mock
    private WritableStates states;

    @Mock
    private WritableKVStateBase<EntityIDPair, TokenRelation> tokenRelState;

    @Mock
    private StoreMetricsService storeMetricsService;

    private WritableTokenRelationStore subject;

    @BeforeEach
    void setUp() {
        given(states.<EntityIDPair, TokenRelation>get(V0490TokenSchema.TOKEN_RELS_KEY))
                .willReturn(tokenRelState);

        subject = new WritableTokenRelationStore(states, CONFIGURATION, storeMetricsService);
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testNullConstructorArgs() {
        assertThrows(
                NullPointerException.class,
                () -> new WritableTokenRelationStore(null, CONFIGURATION, storeMetricsService));
        assertThrows(
                NullPointerException.class, () -> new WritableTokenRelationStore(states, null, storeMetricsService));
        assertThrows(NullPointerException.class, () -> new WritableTokenRelationStore(states, CONFIGURATION, null));
    }

    @Test
    void testPut() {
        final var expectedTokenRel = TokenRelation.newBuilder()
                .accountId(ACCOUNT_20_ID)
                .tokenId(TOKEN_10_ID)
                .build();

        subject.put(expectedTokenRel);
        verify(tokenRelState)
                .put(
                        EntityIDPair.newBuilder()
                                .accountId(ACCOUNT_20_ID)
                                .tokenId(TOKEN_10_ID)
                                .build(),
                        expectedTokenRel);
    }

    @Test
    void testPutNull() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> subject.put(null));
    }

    @Test
    void testGet() {
        final var tokenRelation = TokenRelation.newBuilder()
                .tokenId(TOKEN_10_ID)
                .accountId(ACCOUNT_20_ID)
                .build();
        given(tokenRelState.get(EntityIDPair.newBuilder()
                        .accountId(ACCOUNT_20_ID)
                        .tokenId(TOKEN_10_ID)
                        .build()))
                .willReturn(tokenRelation);

        final var result = subject.get(ACCOUNT_20_ID, TOKEN_10_ID);
        Assertions.assertThat(result).isEqualTo(tokenRelation);
    }

    @Test
    void testGetEmpty() {
        given(tokenRelState.get(notNull())).willReturn(null);

        final var result =
                subject.get(ACCOUNT_20_ID, TokenID.newBuilder().tokenNum(-1L).build());
        Assertions.assertThat(result).isNull();
    }

    @Test
    void testGetForModify() {
        TokenRelation tokenRelation = mock(TokenRelation.class);
        given(tokenRelState.get(EntityIDPair.newBuilder()
                        .accountId(ACCOUNT_20_ID)
                        .tokenId(TOKEN_10_ID)
                        .build()))
                .willReturn(tokenRelation);

        final var result = subject.get(ACCOUNT_20_ID, TOKEN_10_ID);
        Assertions.assertThat(result).isEqualTo(tokenRelation);
    }

    @Test
    void testGetForModifyEmpty() {
        given(tokenRelState.get(EntityIDPair.newBuilder()
                        .accountId(asAccount(-2L))
                        .tokenId(TOKEN_10_ID)
                        .build()))
                .willReturn(null);

        final var result = subject.get(AccountID.newBuilder().accountNum(-2L).build(), TOKEN_10_ID);
        Assertions.assertThat(result).isNull();
    }

    @Test
    void testSizeOfState() {
        final var expectedSize = 3L;
        given(tokenRelState.size()).willReturn(expectedSize);

        final var result = subject.sizeOfState();
        Assertions.assertThat(result).isEqualTo(expectedSize);
    }

    @Test
    void testModifiedTokens() {
        final var modifiedKeys = Set.of(
                EntityIDPair.newBuilder()
                        .accountId(ACCOUNT_20_ID)
                        .tokenId(TOKEN_10_ID)
                        .build(),
                EntityIDPair.newBuilder()
                        .accountId(asAccount(1L))
                        .tokenId(asToken(2L))
                        .build());
        given(tokenRelState.modifiedKeys()).willReturn(modifiedKeys);

        final var result = subject.modifiedTokens();
        Assertions.assertThat(result).isEqualTo(modifiedKeys);
    }
}
