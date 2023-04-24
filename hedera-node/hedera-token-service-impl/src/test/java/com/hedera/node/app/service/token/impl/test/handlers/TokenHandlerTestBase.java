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

package com.hedera.node.app.service.token.impl.test.handlers;

import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.protoToPbj;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.KeyUtils.A_COMPLEX_KEY;
import static com.hedera.test.utils.KeyUtils.B_COMPLEX_KEY;
import static com.hedera.test.utils.KeyUtils.C_COMPLEX_KEY;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenSupplyType;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.token.impl.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.spi.fixtures.state.MapReadableKVState;
import com.hedera.node.app.spi.fixtures.state.MapWritableKVState;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.state.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TokenHandlerTestBase {
    protected static final String TOKENS = "TOKENS";
    protected static final Key payerKey = A_COMPLEX_KEY;
    protected static final HederaKey payerHederaKey = asHederaKey(payerKey).get();
    protected final Key adminKey = A_COMPLEX_KEY;
    protected final Key pauseKey = B_COMPLEX_KEY;
    protected final Key wipeKey = C_COMPLEX_KEY;
    protected final Key kycKey = A_COMPLEX_KEY;
    protected final Key feeScheduleKey = A_COMPLEX_KEY;
    protected final Key supplyKey = A_COMPLEX_KEY;
    protected final Key freezeKey = A_COMPLEX_KEY;
    protected final AccountID payerId = protoToPbj(asAccount("0.0.3"), AccountID.class);
    protected final AccountID treasury = protoToPbj(asAccount("0.0.100"), AccountID.class);
    protected final AccountID autoRenewId = AccountID.newBuilder().accountNum(4).build();
    protected final HederaKey adminHederaKey = asHederaKey(adminKey).get();
    protected final HederaKey wipeHederaKey = asHederaKey(wipeKey).get();
    protected final HederaKey supplyHederaKey = asHederaKey(supplyKey).get();
    protected final HederaKey kycHederaKey = asHederaKey(kycKey).get();
    protected final HederaKey freezeHederaKey = asHederaKey(freezeKey).get();
    protected final HederaKey feeScheduleHederaKey = asHederaKey(feeScheduleKey).get();
    protected final HederaKey pauseHederaKey = asHederaKey(A_COMPLEX_KEY).get();
    protected final EntityNum tokenEntityNum = EntityNum.fromLong(1L);
    protected final TokenID tokenId =
            TokenID.newBuilder().tokenNum(tokenEntityNum.longValue()).build();
    protected final String tokenName = "test token";
    protected final String tokenSymbol = "TT";
    protected final Duration WELL_KNOWN_AUTO_RENEW_PERIOD =
            Duration.newBuilder().seconds(100).build();
    protected final Timestamp WELL_KNOWN_EXPIRY =
            Timestamp.newBuilder().seconds(1_234_567L).build();
    protected final TokenID WELL_KNOWN_TOKEN_ID =
            TokenID.newBuilder().tokenNum(tokenEntityNum.longValue()).build();
    protected final String memo = "test memo";
    protected final long expirationTime = 1_234_567L;
    protected final long sequenceNumber = 1L;
    protected final long autoRenewSecs = 100L;
    protected final Instant consensusTimestamp = Instant.ofEpochSecond(1_234_567L);
    protected final AccountID TEST_DEFAULT_PAYER =
            AccountID.newBuilder().accountNum(13257).build();

    protected Token token;

    @Mock
    protected ReadableStates readableStates;

    @Mock
    protected WritableStates writableStates;

    protected MapReadableKVState<EntityNum, Token> readableTokenState;
    protected MapWritableKVState<EntityNum, Token> writableTokenState;

    protected ReadableTokenStore readableStore;
    protected WritableTokenStore writableStore;

    @BeforeEach
    void commonSetUp() {
        givenValidToken();
        refreshStoresWithCurrentTokenOnlyInReadable();
    }

    protected void refreshStoresWithCurrentTokenOnlyInReadable() {
        readableTokenState = readableTokenState();
        writableTokenState = emptyWritableTokenState();
        given(readableStates.<EntityNum, Token>get(TOKENS)).willReturn(readableTokenState);
        given(writableStates.<EntityNum, Token>get(TOKENS)).willReturn(writableTokenState);
        readableStore = new ReadableTokenStore(readableStates);
        writableStore = new WritableTokenStore(writableStates);
    }

    protected void refreshStoresWithCurrentTokenInWritable() {
        readableTokenState = readableTokenState();
        writableTokenState = writableTokenStateWithOneKey();
        given(readableStates.<EntityNum, Token>get(TOKENS)).willReturn(readableTokenState);
        given(writableStates.<EntityNum, Token>get(TOKENS)).willReturn(writableTokenState);
        readableStore = new ReadableTokenStore(readableStates);
        writableStore = new WritableTokenStore(writableStates);
    }

    @NonNull
    protected MapWritableKVState<EntityNum, Token> emptyWritableTokenState() {
        return MapWritableKVState.<EntityNum, Token>builder(TOKENS).build();
    }

    @NonNull
    protected MapWritableKVState<EntityNum, Token> writableTokenStateWithOneKey() {
        return MapWritableKVState.<EntityNum, Token>builder(TOKENS)
                .value(tokenEntityNum, token)
                .build();
    }

    @NonNull
    protected MapReadableKVState<EntityNum, Token> readableTokenState() {
        return MapReadableKVState.<EntityNum, Token>builder(TOKENS)
                .value(tokenEntityNum, token)
                .build();
    }

    protected void givenValidToken() {
        givenValidToken(autoRenewId.accountNum());
    }

    protected void givenValidToken(long autoRenewAccountNumber) {
        givenValidToken(autoRenewAccountNumber, false, false, false, false, true, true);
    }

    protected void givenValidToken(
            long autoRenewAccountNumber,
            boolean deleted,
            boolean paused,
            boolean accountsFrozenByDefault,
            boolean accountsKycGrantedByDefault,
            boolean withAdminKey,
            boolean withSubmitKey) {
        token = new Token(
                tokenId.tokenNum(),
                tokenName,
                tokenSymbol,
                1000,
                1000,
                treasury.accountNum(),
                adminKey,
                kycKey,
                freezeKey,
                wipeKey,
                supplyKey,
                feeScheduleKey,
                pauseKey,
                0,
                deleted,
                TokenType.FUNGIBLE_COMMON,
                TokenSupplyType.INFINITE,
                autoRenewAccountNumber,
                autoRenewSecs,
                expirationTime,
                memo,
                100000,
                paused,
                accountsFrozenByDefault,
                accountsKycGrantedByDefault,
                Collections.emptyList());
    }

    protected Token createToken() {
        return new Token.Builder()
                .tokenNumber(tokenId.tokenNum())
                .adminKey(adminKey)
                .supplyKey(supplyKey)
                .kycKey(kycKey)
                .freezeKey(freezeKey)
                .wipeKey(wipeKey)
                .feeScheduleKey(feeScheduleKey)
                .pauseKey(pauseKey)
                .treasuryAccountNumber(treasury.accountNum())
                .name(tokenName)
                .symbol(tokenSymbol)
                .totalSupply(1000)
                .decimals(1000)
                .maxSupply(100000)
                .autoRenewSecs(autoRenewSecs)
                .autoRenewAccountNumber(autoRenewId.accountNum())
                .expiry(expirationTime)
                .memo(memo)
                .deleted(false)
                .paused(true)
                .accountsFrozenByDefault(true)
                .accountsKycGrantedByDefault(true)
                .build();
    }

    protected Account newPayerAccount() {
        return new Account(
                2L,
                null,
                payerKey,
                1_234_567L,
                10_000,
                "testAccount",
                false,
                1_234L,
                1_234_568L,
                0,
                true,
                true,
                3,
                2,
                1,
                2,
                10,
                1,
                3,
                false,
                2,
                0,
                1000L,
                2,
                72000,
                0,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                2,
                false);
    }
}
