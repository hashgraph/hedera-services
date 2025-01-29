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

package com.hedera.node.app.service.token.impl.test.handlers.util;

import static com.hedera.node.app.service.token.impl.handlers.BaseTokenHandler.asToken;
import static com.hedera.node.app.service.token.impl.test.handlers.util.CryptoHandlerTestBase.A_COMPLEX_KEY;
import static com.hedera.node.app.service.token.impl.test.handlers.util.CryptoHandlerTestBase.B_COMPLEX_KEY;
import static com.hedera.node.app.service.token.impl.test.handlers.util.CryptoHandlerTestBase.C_COMPLEX_KEY;
import static com.hedera.node.app.service.token.impl.test.util.SigReqAdapterUtils.UNSET_STAKED_ID;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Fraction;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenSupplyType;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.transaction.CustomFee;
import com.hedera.hapi.node.transaction.FixedFee;
import com.hedera.hapi.node.transaction.FractionalFee;
import com.hedera.hapi.node.transaction.RoyaltyFee;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.ReadableTokenStoreImpl;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.spi.ReadableStates;
import com.swirlds.state.spi.WritableStates;
import com.swirlds.state.test.fixtures.MapReadableKVState;
import com.swirlds.state.test.fixtures.MapWritableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
// FUTURE : Remove this and use CryptoTokenHandlerTestBase instead for all classes extending this class

/**
 * Base class for token handler tests.
 */
@ExtendWith(MockitoExtension.class)
public class TokenHandlerTestBase {
    protected static final String TOKENS = "TOKENS";
    protected static final Key payerKey = A_COMPLEX_KEY;
    protected final Key adminKey = A_COMPLEX_KEY;
    protected final Key pauseKey = B_COMPLEX_KEY;
    protected final Key wipeKey = C_COMPLEX_KEY;
    protected final Key kycKey = A_COMPLEX_KEY;
    protected final Key feeScheduleKey = A_COMPLEX_KEY;
    protected final Key supplyKey = A_COMPLEX_KEY;
    protected final Key freezeKey = A_COMPLEX_KEY;
    protected final AccountID payerId = AccountID.newBuilder().accountNum(3).build();
    protected final AccountID treasury = AccountID.newBuilder().accountNum(100).build();
    protected final AccountID autoRenewId = AccountID.newBuilder().accountNum(4).build();
    protected final Bytes metadata = Bytes.wrap(new byte[] {1, 2, 3, 4});
    protected final Key metadataKey = Key.DEFAULT;
    protected final TokenID tokenId = asToken(1L);
    protected final String tokenName = "test token";
    protected final String tokenSymbol = "TT";
    protected final String memo = "test memo";
    protected final long expirationTime = 1_234_567L;
    protected final long autoRenewSecs = 100L;
    protected final Instant consensusTimestamp = Instant.ofEpochSecond(1_234_567L);
    protected final AccountID TEST_DEFAULT_PAYER =
            AccountID.newBuilder().accountNum(13257).build();

    protected FixedFee fixedFee = FixedFee.newBuilder()
            .amount(1_000L)
            .denominatingTokenId(TokenID.newBuilder().tokenNum(1L).build())
            .build();
    protected FractionalFee fractionalFee = FractionalFee.newBuilder()
            .maximumAmount(1_000L)
            .minimumAmount(1L)
            .fractionalAmount(Fraction.newBuilder().numerator(1).denominator(2).build())
            .build();
    protected RoyaltyFee royaltyFee = RoyaltyFee.newBuilder()
            .exchangeValueFraction(
                    Fraction.newBuilder().numerator(1).denominator(2).build())
            .fallbackFee(fixedFee)
            .build();

    protected CustomFee customFee = CustomFee.newBuilder()
            .fixedFee(fixedFee)
            .fractionalFee(fractionalFee)
            .royaltyFee(royaltyFee)
            .build();

    protected Token token;

    @Mock
    protected ReadableStates readableStates;

    @Mock
    protected WritableStates writableStates;

    protected MapReadableKVState<TokenID, Token> readableTokenState;
    protected MapWritableKVState<TokenID, Token> writableTokenState;

    protected ReadableTokenStore readableTokenStore;
    protected WritableTokenStore writableTokenStore;

    /**
     * Sets up the common test environment.
     */
    @BeforeEach
    public void commonSetUp() {
        givenValidToken();
        refreshStoresWithCurrentTokenOnlyInReadable();
    }

    protected void refreshStoresWithCurrentTokenOnlyInReadable() {
        readableTokenState = readableTokenState();
        writableTokenState = emptyWritableTokenState();
        given(readableStates.<TokenID, Token>get(TOKENS)).willReturn(readableTokenState);
        given(writableStates.<TokenID, Token>get(TOKENS)).willReturn(writableTokenState);
        readableTokenStore = new ReadableTokenStoreImpl(readableStates);
        final var configuration = HederaTestConfigBuilder.createConfig();
        writableTokenStore = new WritableTokenStore(writableStates, configuration, mock(StoreMetricsService.class));
    }

    protected void refreshStoresWithCurrentTokenInWritable() {
        readableTokenState = readableTokenState();
        writableTokenState = writableTokenStateWithOneKey();
        given(readableStates.<TokenID, Token>get(TOKENS)).willReturn(readableTokenState);
        given(writableStates.<TokenID, Token>get(TOKENS)).willReturn(writableTokenState);
        readableTokenStore = new ReadableTokenStoreImpl(readableStates);
        final var configuration = HederaTestConfigBuilder.createConfig();
        writableTokenStore = new WritableTokenStore(writableStates, configuration, mock(StoreMetricsService.class));
    }

    @NonNull
    protected MapWritableKVState<TokenID, Token> emptyWritableTokenState() {
        return MapWritableKVState.<TokenID, Token>builder(TOKENS).build();
    }

    @NonNull
    protected MapWritableKVState<TokenID, Token> writableTokenStateWithOneKey() {
        return MapWritableKVState.<TokenID, Token>builder(TOKENS)
                .value(tokenId, token)
                .build();
    }

    @NonNull
    protected MapReadableKVState<TokenID, Token> readableTokenState() {
        return MapReadableKVState.<TokenID, Token>builder(TOKENS)
                .value(tokenId, token)
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
                tokenId,
                tokenName,
                tokenSymbol,
                1000,
                1000,
                AccountID.newBuilder().accountNum(treasury.accountNum()).build(),
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
                BaseCryptoHandler.asAccount(autoRenewAccountNumber),
                autoRenewSecs,
                expirationTime,
                memo,
                100000,
                paused,
                accountsFrozenByDefault,
                accountsKycGrantedByDefault,
                Collections.emptyList(),
                metadata,
                metadataKey);
    }

    protected Token createToken() {
        return new Token.Builder()
                .tokenId(tokenId)
                .adminKey(adminKey)
                .supplyKey(supplyKey)
                .kycKey(kycKey)
                .freezeKey(freezeKey)
                .wipeKey(wipeKey)
                .feeScheduleKey(feeScheduleKey)
                .pauseKey(pauseKey)
                .treasuryAccountId(
                        AccountID.newBuilder().accountNum(treasury.accountNum()).build())
                .name(tokenName)
                .symbol(tokenSymbol)
                .totalSupply(1000)
                .decimals(1000)
                .maxSupply(100000)
                .autoRenewSeconds(autoRenewSecs)
                .autoRenewAccountId(autoRenewId)
                .expirationSecond(expirationTime)
                .memo(memo)
                .deleted(false)
                .paused(true)
                .accountsFrozenByDefault(true)
                .accountsKycGrantedByDefault(true)
                .customFees(List.of(customFee))
                .build();
    }

    protected Account newPayerAccount() {
        return new Account(
                AccountID.newBuilder().accountNum(2L).build(),
                null,
                payerKey,
                1_234_567L,
                10_000,
                "testAccount",
                false,
                1_234L,
                1_234_568L,
                UNSET_STAKED_ID,
                true,
                true,
                TokenID.newBuilder().tokenNum(3L).build(),
                NftID.newBuilder().tokenId(TokenID.newBuilder().tokenNum(2L)).build(),
                1,
                2,
                10,
                1,
                3,
                false,
                2,
                0,
                1000L,
                AccountID.newBuilder().accountNum(2L).build(),
                72000,
                0,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                2,
                false,
                null,
                null,
                0);
    }
}
