/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.fromGrpcKey;
import static com.hedera.node.app.service.mono.utils.MiscUtils.asKeyUnchecked;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.FcCustomFee;
import com.hedera.node.app.service.mono.state.submerkle.FixedFeeSpec;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.token.impl.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.test.handlers.TokenHandlerTestBase;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableStates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReadableTokenStoreTest extends TokenHandlerTestBase {
    private final EntityNum tokenEntityNum = EntityNum.fromLong(2000);
    @Mock private ReadableKVState<EntityNum, Token> tokens;

    private static final String TOKENS = "TOKENS";
    private final TokenID tokenId = TokenID.newBuilder().tokenNum(2000).build();
    @Mock private ReadableStates states;
    private Token token;

    private ReadableTokenStore subject;

    @BeforeEach
    public void setUp() {
        initializeToken();
        subject = new ReadableTokenStore(states);
    }

    private void initializeToken() {
        given(states.<EntityNum, Token>get(TOKENS)).willReturn(tokens);
        token = createToken();
    }

    @Test
    void getsMerkleTokenIfTokenIdPresent() {
        given(tokens.get(tokenEntityNum)).willReturn(token);

        final var result = subject.getTokenMeta(tokenId);

        assertFalse(result.failed());
        assertNull(result.failureReason());

        final var meta = result.metadata();
        assertEquals(adminKey, fromGrpcKey(asKeyUnchecked((JKey) meta.adminKey().get())));
        assertEquals(kycKey, fromGrpcKey(asKeyUnchecked((JKey) meta.kycKey().get())));
        assertEquals(wipeKey, fromGrpcKey(asKeyUnchecked((JKey) meta.wipeKey().get())));
        assertEquals(freezeKey, fromGrpcKey(asKeyUnchecked((JKey) meta.freezeKey().get())));
        assertEquals(supplyKey, fromGrpcKey(asKeyUnchecked((JKey) meta.supplyKey().get())));
        assertEquals(
                feeScheduleKey, fromGrpcKey(asKeyUnchecked((JKey) meta.feeScheduleKey().get())));
        assertEquals(pauseKey, fromGrpcKey(asKeyUnchecked((JKey) meta.pauseKey().get())));
        assertFalse(meta.hasRoyaltyWithFallback());
        assertEquals(treasury.accountNum(), meta.treasuryNum());
    }

    @Test
    void getsNullKeyIfMissingAccount() {
        given(tokens.get(tokenEntityNum)).willReturn(null);

        final var result = subject.getTokenMeta(tokenId);

        assertTrue(result.failed());
        assertEquals(INVALID_TOKEN_ID, result.failureReason());
        assertNull(result.metadata());
    }

    @Test
    void classifiesRoyaltyWithFallback() {
        final var copy = token.copyBuilder();
        copy.tokenType(NON_FUNGIBLE_UNIQUE);
        copy.customFees(
                PbjConverter.fromFcCustomFee(
                        FcCustomFee.royaltyFee(
                                1, 2, new FixedFeeSpec(1, null), new EntityId(1, 2, 5), false)));

        given(tokens.get(tokenEntityNum)).willReturn(copy.build());

        final var result = subject.getTokenMeta(tokenId);

        assertFalse(result.failed());
        assertNull(result.failureReason());
        assertTrue(result.metadata().hasRoyaltyWithFallback());
        assertSame(treasury.accountNum(), result.metadata().treasuryNum());
    }

    @Test
    void classifiesRoyaltyWithNoFallback() {
        final var copy = token.copyBuilder();
        copy.tokenType(NON_FUNGIBLE_UNIQUE);
        copy.customFees(
                PbjConverter.fromFcCustomFee(
                        FcCustomFee.royaltyFee(1, 2, null, new EntityId(1, 2, 5), false)));

        given(tokens.get(tokenEntityNum)).willReturn(copy.build());

        final var result = subject.getTokenMeta(tokenId);

        assertFalse(result.failed());
        assertNull(result.failureReason());
        assertFalse(result.metadata().hasRoyaltyWithFallback());
        assertSame(treasury.accountNum(), result.metadata().treasuryNum());
    }
}
