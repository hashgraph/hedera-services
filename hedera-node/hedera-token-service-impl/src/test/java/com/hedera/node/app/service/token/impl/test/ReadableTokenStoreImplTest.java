/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import static com.hedera.hapi.node.base.TokenType.NON_FUNGIBLE_UNIQUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.FcCustomFee;
import com.hedera.node.app.service.mono.state.submerkle.FixedFeeSpec;
import com.hedera.node.app.service.token.impl.ReadableTokenStoreImpl;
import com.hedera.node.app.service.token.impl.test.handlers.util.TokenHandlerTestBase;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.swirlds.platform.state.spi.ReadableKVState;
import com.swirlds.platform.state.spi.ReadableStates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReadableTokenStoreImplTest extends TokenHandlerTestBase {
    @Mock
    private ReadableKVState<TokenID, Token> tokens;

    private static final String TOKENS = "TOKENS";
    private final TokenID tokenId = TokenID.newBuilder().tokenNum(2000).build();

    @Mock
    private ReadableStates states;

    private Token token;

    private ReadableTokenStoreImpl subject;

    @BeforeEach
    public void setUp() {
        initializeToken();
        subject = new ReadableTokenStoreImpl(states);
    }

    private void initializeToken() {
        given(states.<TokenID, Token>get(TOKENS)).willReturn(tokens);
        token = createToken();
    }

    @Test
    void getsMerkleTokenIfTokenIdPresent() {
        given(tokens.get(tokenId)).willReturn(token);

        final var meta = subject.getTokenMeta(tokenId);
        assertEquals(adminKey, meta.adminKey());
        assertEquals(kycKey, meta.kycKey());
        assertEquals(wipeKey, meta.wipeKey());
        assertEquals(freezeKey, meta.freezeKey());
        assertEquals(supplyKey, meta.supplyKey());
        assertEquals(feeScheduleKey, meta.feeScheduleKey());
        assertEquals(pauseKey, meta.pauseKey());
        assertTrue(meta.hasRoyaltyWithFallback());
        assertEquals(treasury, meta.treasuryAccountId());
    }

    @Test
    void getsNullKeyIfMissingAccount() throws PreCheckException {
        given(tokens.get(tokenId)).willReturn(null);
        assertNull(subject.getTokenMeta(tokenId));
    }

    @Test
    void classifiesRoyaltyWithFallback() throws PreCheckException {
        final var copy = token.copyBuilder();
        copy.tokenType(NON_FUNGIBLE_UNIQUE);
        copy.customFees(PbjConverter.fromFcCustomFee(
                FcCustomFee.royaltyFee(1, 2, new FixedFeeSpec(1, null), new EntityId(1, 2, 5), false)));

        given(tokens.get(tokenId)).willReturn(copy.build());

        final var meta = subject.getTokenMeta(tokenId);

        assertTrue(meta.hasRoyaltyWithFallback());
        assertEquals(treasury, meta.treasuryAccountId());
    }

    @Test
    void classifiesRoyaltyWithNoFallback() throws PreCheckException {
        final var copy = token.copyBuilder();
        copy.tokenType(NON_FUNGIBLE_UNIQUE);
        copy.customFees(PbjConverter.fromFcCustomFee(FcCustomFee.royaltyFee(1, 2, null, new EntityId(1, 2, 5), false)));

        given(tokens.get(tokenId)).willReturn(copy.build());

        final var meta = subject.getTokenMeta(tokenId);

        assertFalse(meta.hasRoyaltyWithFallback());
        assertEquals(treasury, meta.treasuryAccountId());
    }

    @Test
    void returnSizeOfState() {
        final var store = new ReadableTokenStoreImpl(readableStates);
        assertEquals(readableStates.get(TOKENS).size(), store.sizeOfState());
    }

    @Test
    void warmWarmsUnderlyingState(@Mock ReadableKVState<EntityIDPair, TokenRelation> tokenRelations) {
        subject.warm(tokenId);
        verify(tokens).warm(tokenId);
    }
}
