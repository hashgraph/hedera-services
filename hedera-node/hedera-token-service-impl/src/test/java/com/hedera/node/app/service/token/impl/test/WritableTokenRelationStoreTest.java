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

package com.hedera.node.app.service.token.impl.test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.mock;

import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.node.app.service.mono.utils.EntityNumPair;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.spi.state.WritableKVStateBase;
import com.hedera.node.app.spi.state.WritableStates;
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
    private static final long ACCOUNT_20 = 20L;

    @Mock
    private WritableStates states;

    @Mock
    private WritableKVStateBase<EntityNumPair, TokenRelation> tokenRelState;

    private WritableTokenRelationStore subject;

    @BeforeEach
    void setUp() {
        given(states.<EntityNumPair, TokenRelation>get("TOKEN_RELATIONS")).willReturn(tokenRelState);

        subject = new WritableTokenRelationStore(states);
    }

    @Test
    void testNullConstructorArgs() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> new WritableTokenRelationStore(null));
    }

    @Test
    void testPut() {
        final var expectedTokenRel = TokenRelation.newBuilder()
                .tokenNumber(TOKEN_10)
                .accountNumber(ACCOUNT_20)
                .build();
        final var expectedEntityNumPair = EntityNumPair.fromLongs(TOKEN_10, ACCOUNT_20);

        subject.put(expectedTokenRel);
        verify(tokenRelState).put(expectedEntityNumPair, expectedTokenRel);
    }

    @Test
    void testPutNull() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> subject.put(null));
    }

    @Test
    void testCommit() {
        subject.commit();
        verify(tokenRelState).commit();
    }

    @Test
    void testGet() {
        final var tokenRelation = TokenRelation.newBuilder()
                .tokenNumber(TOKEN_10)
                .accountNumber(ACCOUNT_20)
                .build();
        given(tokenRelState.get(notNull())).willReturn(tokenRelation);

        final var result = subject.get(TOKEN_10, ACCOUNT_20);
        Assertions.assertThat(result.orElseThrow()).isEqualTo(tokenRelation);
    }

    @Test
    void testGetEmpty() {
        given(tokenRelState.get(notNull())).willReturn(null);

        final var result = subject.get(-1L, ACCOUNT_20);
        Assertions.assertThat(result).isEmpty();
    }

    @Test
    void testGetForModify() {
        TokenRelation tokenRelation = mock(TokenRelation.class);
        given(tokenRelState.getForModify(notNull())).willReturn(tokenRelation);

        final var result = subject.getForModify(TOKEN_10, ACCOUNT_20);
        Assertions.assertThat(result.orElseThrow()).isEqualTo(tokenRelation);
    }

    @Test
    void testGetForModifyEmpty() {
        given(tokenRelState.getForModify(notNull())).willReturn(null);

        final var result = subject.getForModify(TOKEN_10, -2L);
        Assertions.assertThat(result).isEmpty();
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
        final var modifiedKeys = Set.of(EntityNumPair.fromLongs(TOKEN_10, ACCOUNT_20), EntityNumPair.fromLongs(1L, 2L));
        given(tokenRelState.modifiedKeys()).willReturn(modifiedKeys);

        final var result = subject.modifiedTokens();
        Assertions.assertThat(result).isEqualTo(modifiedKeys);
    }
}
