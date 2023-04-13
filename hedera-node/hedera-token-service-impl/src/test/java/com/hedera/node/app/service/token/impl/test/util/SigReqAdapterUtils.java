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

package com.hedera.node.app.service.token.impl.test.util;

import static com.hedera.node.app.service.mono.pbj.PbjConverter.fromFcCustomFee;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.fromGrpcKey;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.fromPbj;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.toPbj;
import static com.hedera.node.app.service.mono.utils.MiscUtils.asKeyUnchecked;
import static com.hedera.node.app.service.token.impl.test.handlers.AdapterUtils.mockStates;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.KNOWN_TOKEN_IMMUTABLE;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.KNOWN_TOKEN_NO_SPECIAL_KEYS;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.KNOWN_TOKEN_WITH_FEE_SCHEDULE_KEY;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.KNOWN_TOKEN_WITH_FREEZE;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.KNOWN_TOKEN_WITH_KYC;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.KNOWN_TOKEN_WITH_PAUSE;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.KNOWN_TOKEN_WITH_ROYALTY_FEE_AND_FALLBACK;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.KNOWN_TOKEN_WITH_SUPPLY;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.KNOWN_TOKEN_WITH_WIPE;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.transaction.CustomFee;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.accessors.PlatformTxnAccessor;
import com.hedera.node.app.service.token.impl.ReadableTokenStore;
import com.hedera.node.app.spi.fixtures.state.MapReadableKVState;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.StateKeyAdapter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.apache.commons.lang3.NotImplementedException;

public class SigReqAdapterUtils {
    private static final String TOKENS_KEY = "TOKENS";

    /**
     * Returns the {@link ReadableTokenStore} containing the "well-known" tokens that exist in a
     * {@code SigRequirementsTest} scenario. This allows us to re-use these scenarios in unit tests
     * that require a {@link ReadableTokenStore}.
     *
     * @return the well-known token store
     */
    public static ReadableTokenStore wellKnownTokenStoreAt() {
        final var source = sigReqsMockTokenStore();
        final Map<EntityNum, Token> destination = new HashMap<>();
        List.of(
                        toPbj(KNOWN_TOKEN_IMMUTABLE),
                        toPbj(KNOWN_TOKEN_NO_SPECIAL_KEYS),
                        toPbj(KNOWN_TOKEN_WITH_PAUSE),
                        toPbj(KNOWN_TOKEN_WITH_FREEZE),
                        toPbj(KNOWN_TOKEN_WITH_KYC),
                        toPbj(KNOWN_TOKEN_WITH_FEE_SCHEDULE_KEY),
                        toPbj(KNOWN_TOKEN_WITH_ROYALTY_FEE_AND_FALLBACK),
                        toPbj(KNOWN_TOKEN_WITH_SUPPLY),
                        toPbj(KNOWN_TOKEN_WITH_WIPE))
                .forEach(id -> destination.put(EntityNum.fromLong(id.tokenNum()), asToken(source.get(fromPbj(id)))));
        final var wrappedState = new MapReadableKVState<>("TOKENS", destination);
        final var state = new StateKeyAdapter<>(wrappedState, Function.identity());
        return new ReadableTokenStore(mockStates(Map.of(TOKENS_KEY, state)));
    }

    @SuppressWarnings("java:S1604")
    private static com.hedera.node.app.service.mono.store.tokens.TokenStore sigReqsMockTokenStore() {
        final var dummyScenario = new TxnHandlingScenario() {
            @Override
            public PlatformTxnAccessor platformTxn() {
                throw new NotImplementedException();
            }
        };
        return dummyScenario.tokenStore();
    }

    public static TransactionBody txnFrom(final TxnHandlingScenario scenario) {
        try {
            return toPbj(scenario.platformTxn().getTxn());
        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static Token asToken(final MerkleToken token) {
        final var customFee = token.customFeeSchedule();
        final List<CustomFee> pbjFees = new ArrayList<>();
        if (customFee != null) {
            customFee.forEach(fee -> pbjFees.add(fromFcCustomFee(fee)));
        }
        return new Token(
                token.entityNum(),
                token.name(),
                token.symbol(),
                token.decimals(),
                token.totalSupply(),
                token.treasuryNum().longValue(),
                !token.adminKey().isEmpty()
                        ? fromGrpcKey(asKeyUnchecked(token.adminKey().get()))
                        : Key.DEFAULT,
                !token.kycKey().isEmpty()
                        ? fromGrpcKey(asKeyUnchecked(token.kycKey().get()))
                        : Key.DEFAULT,
                !token.freezeKey().isEmpty()
                        ? fromGrpcKey(asKeyUnchecked(token.freezeKey().get()))
                        : Key.DEFAULT,
                !token.wipeKey().isEmpty()
                        ? fromGrpcKey(asKeyUnchecked(token.wipeKey().get()))
                        : Key.DEFAULT,
                !token.supplyKey().isEmpty()
                        ? fromGrpcKey(asKeyUnchecked(token.getSupplyKey()))
                        : Key.DEFAULT,
                !token.feeScheduleKey().isEmpty()
                        ? fromGrpcKey(asKeyUnchecked(token.feeScheduleKey().get()))
                        : Key.DEFAULT,
                !token.pauseKey().isEmpty()
                        ? fromGrpcKey(asKeyUnchecked(token.pauseKey().get()))
                        : Key.DEFAULT,
                token.getLastUsedSerialNumber(),
                token.isDeleted(),
                token.tokenType()
                                == com.hedera.node.app.service.evm.store.tokens.TokenType
                                        .FUNGIBLE_COMMON
                        ? com.hedera.hapi.node.base.TokenType.FUNGIBLE_COMMON
                        : com.hedera.hapi.node.base.TokenType.NON_FUNGIBLE_UNIQUE,
                token.supplyType()
                                == com.hedera.node.app.service.mono.state.enums.TokenSupplyType
                                        .FINITE
                        ? com.hedera.hapi.node.base.TokenSupplyType.FINITE
                        : com.hedera.hapi.node.base.TokenSupplyType.INFINITE,
                token.autoRenewAccount() != null ? token.autoRenewAccount().num() : 0,
                token.autoRenewPeriod(),
                token.expiry(),
                token.memo(),
                token.maxSupply(),
                token.isPaused(),
                token.accountsAreFrozenByDefault(),
                token.accountsAreFrozenByDefault(),
                pbjFees);
    }
}
