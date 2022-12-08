package com.hedera.test.utils;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.state.impl.InMemoryStateImpl;
import com.hedera.node.app.service.mono.state.impl.RebuiltStateImpl;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.token.impl.AccountStore;
import com.hedera.node.app.service.mono.token.impl.TokenStore;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.accessors.PlatformTxnAccessor;
import com.hedera.node.app.spi.state.State;
import com.hedera.node.app.spi.state.States;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.swirlds.merkle.map.MerkleMap;
import org.apache.commons.lang3.NotImplementedException;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.hedera.node.app.service.mono.utils.EntityNum.MISSING_NUM;
import static com.hedera.node.app.service.mono.utils.EntityNum.fromAccountId;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.CURRENTLY_UNUSED_ALIAS;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.FIRST_TOKEN_SENDER;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.FIRST_TOKEN_SENDER_LITERAL_ALIAS;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.KNOWN_TOKEN_IMMUTABLE;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.KNOWN_TOKEN_NO_SPECIAL_KEYS;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.KNOWN_TOKEN_WITH_FEE_SCHEDULE_KEY;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.KNOWN_TOKEN_WITH_FREEZE;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.KNOWN_TOKEN_WITH_KYC;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.KNOWN_TOKEN_WITH_PAUSE;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.KNOWN_TOKEN_WITH_ROYALTY_FEE_AND_FALLBACK;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.KNOWN_TOKEN_WITH_SUPPLY;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.KNOWN_TOKEN_WITH_WIPE;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.NO_RECEIVER_SIG;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.NO_RECEIVER_SIG_ALIAS;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.RECEIVER_SIG;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.RECEIVER_SIG_ALIAS;
import static org.mockito.BDDMockito.given;

public class AdapterUtils {
    private static final String TOKENS_KEY = "TOKENS";
    private static final String ACCOUNTS_KEY = "ACCOUNTS";
    private static final String ALIASES_KEY = "ALIASES";

    public static AccountStore wellKnownAccountStoreAt(final Instant mockLastModified) {
        return new AccountStore(
                mockStates(Map.of(
                        ALIASES_KEY, wellKnownAliasState(mockLastModified),
                        ACCOUNTS_KEY,wellKnownAccountsState(mockLastModified))));
    }

    public static TokenStore wellKnownTokenStoreAt(final Instant mockLastModified) {
        final var source = sigReqsMockTokenStore();
        final MerkleMap<EntityNum, MerkleToken> destination = new MerkleMap<>();
        List.of(KNOWN_TOKEN_IMMUTABLE,
                KNOWN_TOKEN_NO_SPECIAL_KEYS,
                KNOWN_TOKEN_WITH_PAUSE,
                KNOWN_TOKEN_WITH_FREEZE,
                KNOWN_TOKEN_WITH_KYC,
                KNOWN_TOKEN_WITH_FEE_SCHEDULE_KEY,
                KNOWN_TOKEN_WITH_ROYALTY_FEE_AND_FALLBACK,
                KNOWN_TOKEN_WITH_SUPPLY,
                KNOWN_TOKEN_WITH_WIPE).forEach(id ->
                    destination.put(EntityNum.fromTokenId(id), source.get(id)));
        final var wrappedState = new InMemoryStateImpl<>(
                TOKENS_KEY,
                destination,
                mockLastModified);
        final var state = new StateKeyAdapter<>(wrappedState, EntityNum::fromLong);
        return new TokenStore(mockStates(Map.of(TOKENS_KEY, state)));
    }

    private static States mockStates(final Map<String, State> keysToMock) {
        final var mockStates = Mockito.mock(States.class);
        keysToMock.forEach((key, state) -> given(mockStates.get(key)).willReturn(state));
        return mockStates;
    }

    private static State<Long, ? extends HederaAccount> wellKnownAccountsState(
            final Instant mockLastModified) {
        final var wrappedState = new InMemoryStateImpl<>(
                        ACCOUNTS_KEY,
                        TxnHandlingScenario.wellKnownAccounts(),
                        mockLastModified);
        return new StateKeyAdapter<>(wrappedState, EntityNum::fromLong);
    }

    private static State<ByteString, Long> wellKnownAliasState(
            final Instant mockLastModified) {
        final var wellKnownAliases = Map.ofEntries(
                Map.entry(ByteString.copyFromUtf8(CURRENTLY_UNUSED_ALIAS), MISSING_NUM.longValue()),
                Map.entry(ByteString.copyFromUtf8(NO_RECEIVER_SIG_ALIAS), fromAccountId(NO_RECEIVER_SIG).longValue()),
                Map.entry(ByteString.copyFromUtf8(RECEIVER_SIG_ALIAS), fromAccountId(RECEIVER_SIG).longValue()),
                Map.entry(FIRST_TOKEN_SENDER_LITERAL_ALIAS, fromAccountId(FIRST_TOKEN_SENDER).longValue()));
        return new RebuiltStateImpl<>(ALIASES_KEY, wellKnownAliases, mockLastModified);
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
}
