/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.service.mono.state.enums.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.mono.legacy.core.jproto.JEd25519Key;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.enums.TokenSupplyType;
import com.hedera.node.app.service.mono.state.enums.TokenType;
import com.hedera.node.app.service.mono.state.impl.InMemoryStateImpl;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.FcCustomFee;
import com.hedera.node.app.service.mono.state.submerkle.FixedFeeSpec;
import com.hedera.node.app.service.token.impl.ReadableTokenStore;
import com.hedera.node.app.spi.state.States;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReadableTokenStoreTest {
    @Mock private InMemoryStateImpl tokens;
    @Mock private States states;
    private static final String TOKENS = "TOKENS";
    private final TokenID tokenId = IdUtils.asToken("0.0.2000");
    private final String symbol = "TestToken";
    private final String name = "TestTokenName";
    private final int decimals = 2;
    private final long expiry = 1_234_567L;
    private final long otherTotalSupply = 1_000_001L;
    private final boolean freezeDefault = true;
    private final boolean accountsKycGrantedByDefault = true;
    private final EntityId treasury = new EntityId(1, 2, 3);
    private final JKey adminKey = new JEd25519Key("not-a-real-adminKey".getBytes());
    private final JKey freezeKey = new JEd25519Key("not-a-real-freezeKey".getBytes());
    private final JKey kycKey = new JEd25519Key("not-a-real-kycKey".getBytes());
    private final JKey wipeKey = new JEd25519Key("not-a-real-wipeKey".getBytes());
    private final JKey supplyKey = new JEd25519Key("not-a-real-supplyKey".getBytes());
    private final JKey feeScheduleKey = new JEd25519Key("not-a-real-feeScheduleKey".getBytes());
    private final JKey pauseKey = new JEd25519Key("not-a-real-pauseKey".getBytes());
    private final MerkleToken token =
            new MerkleToken(
                    expiry,
                    otherTotalSupply,
                    decimals,
                    symbol,
                    name,
                    freezeDefault,
                    accountsKycGrantedByDefault,
                    treasury);

    private ReadableTokenStore subject;

    @BeforeEach
    public void setUp() {
        initializeToken();
        subject = new ReadableTokenStore(states);
    }

    private void initializeToken() {
        given(states.get(TOKENS)).willReturn(tokens);
        token.setTotalSupply(100L);
        token.setAdminKey(adminKey);
        token.setFreezeKey(freezeKey);
        token.setKycKey(kycKey);
        token.setWipeKey(wipeKey);
        token.setSupplyKey(supplyKey);
        token.setFeeScheduleKey(feeScheduleKey);
        token.setPauseKey(pauseKey);
        token.setDeleted(false);
        token.setMemo("memo");
        token.setTokenType(TokenType.NON_FUNGIBLE_UNIQUE);
        token.setSupplyType(TokenSupplyType.INFINITE);
        token.setAccountsFrozenByDefault(true);
        token.setFeeSchedule(
                List.of(
                        FcCustomFee.fixedFee(
                                1, new EntityId(1, 2, 5), new EntityId(1, 2, 5), false)));
    }

    @Test
    void getsMerkleTokenIfTokenIdPresent() {
        given(tokens.get(tokenId.getTokenNum())).willReturn(Optional.of(token));

        final var result = subject.getTokenMeta(tokenId);

        assertFalse(result.failed());
        assertNull(result.failureReason());

        final var meta = result.metadata();
        assertEquals(adminKey, meta.adminKey().get());
        assertEquals(kycKey, meta.kycKey().get());
        assertEquals(wipeKey, meta.wipeKey().get());
        assertEquals(freezeKey, meta.freezeKey().get());
        assertEquals(supplyKey, meta.supplyKey().get());
        assertEquals(feeScheduleKey, meta.feeScheduleKey().get());
        assertEquals(pauseKey, meta.pauseKey().get());
        assertFalse(meta.hasRoyaltyWithFallback());
        assertEquals(treasury, meta.treasury());
    }

    @Test
    void getsNullKeyIfMissingAccount() {
        given(tokens.get(tokenId.getTokenNum())).willReturn(Optional.empty());

        final var result = subject.getTokenMeta(tokenId);

        assertTrue(result.failed());
        assertEquals(INVALID_TOKEN_ID, result.failureReason());
        assertNull(result.metadata());
    }

    @Test
    void classifiesRoyaltyWithFallback() {
        token.setTokenType(NON_FUNGIBLE_UNIQUE);
        token.setFeeSchedule(
                List.of(
                        FcCustomFee.royaltyFee(
                                1, 2, new FixedFeeSpec(1, null), new EntityId(1, 2, 5), false)));
        given(tokens.get(tokenId.getTokenNum())).willReturn(Optional.of(token));

        final var result = subject.getTokenMeta(tokenId);

        assertFalse(result.failed());
        assertNull(result.failureReason());
        assertTrue(result.metadata().hasRoyaltyWithFallback());
        assertSame(treasury, result.metadata().treasury());
    }

    @Test
    void classifiesRoyaltyWithNoFallback() {
        token.setTokenType(NON_FUNGIBLE_UNIQUE);
        token.setFeeSchedule(
                List.of(FcCustomFee.royaltyFee(1, 2, null, new EntityId(1, 2, 5), false)));
        given(tokens.get(tokenId.getTokenNum())).willReturn(Optional.of(token));

        final var result = subject.getTokenMeta(tokenId);

        assertFalse(result.failed());
        assertNull(result.failureReason());
        assertFalse(result.metadata().hasRoyaltyWithFallback());
        assertSame(treasury, result.metadata().treasury());
    }
}
