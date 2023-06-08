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

import static com.hedera.node.app.service.mono.context.BasicTransactionContext.EMPTY_KEY;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.fromFcCustomFee;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.fromGrpcKey;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.fromPbj;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.toPbj;
import static com.hedera.node.app.service.mono.utils.MiscUtils.asKeyUnchecked;
import static com.hedera.node.app.service.token.impl.TokenServiceImpl.ALIASES_KEY;
import static com.hedera.node.app.service.token.impl.TokenServiceImpl.TOKEN_RELS_KEY;
import static com.hedera.node.app.service.token.impl.test.handlers.util.AdapterUtils.mockStates;
import static com.hedera.node.app.service.token.impl.test.handlers.util.AdapterUtils.mockWritableStates;
import static com.hedera.node.app.service.token.impl.test.handlers.util.AdapterUtils.wellKnownAliasState;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.COMPLEX_KEY_ACCOUNT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.CUSTOM_PAYER_ACCOUNT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.CUSTOM_PAYER_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.DEFAULT_BALANCE;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.DEFAULT_PAYER_BALANCE;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.DELEGATING_SPENDER;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.DELEGATING_SPENDER_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.DELETED_TOKEN;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.DILIGENT_SIGNING_PAYER;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.DILIGENT_SIGNING_PAYER_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.FIRST_TOKEN_SENDER;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.FIRST_TOKEN_SENDER_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.FROM_OVERLAP_PAYER;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.FROM_OVERLAP_PAYER_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.KNOWN_TOKEN_IMMUTABLE;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.KNOWN_TOKEN_NO_SPECIAL_KEYS;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.KNOWN_TOKEN_WITH_FEE_SCHEDULE_KEY;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.KNOWN_TOKEN_WITH_FREEZE;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.KNOWN_TOKEN_WITH_KYC;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.KNOWN_TOKEN_WITH_PAUSE;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.KNOWN_TOKEN_WITH_ROYALTY_FEE_AND_FALLBACK;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.KNOWN_TOKEN_WITH_SUPPLY;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.KNOWN_TOKEN_WITH_WIPE;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISC_ACCOUNT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISC_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.NO_RECEIVER_SIG;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.NO_RECEIVER_SIG_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.OWNER_ACCOUNT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.OWNER_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.RECEIVER_SIG;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.RECEIVER_SIG_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.SECOND_TOKEN_SENDER;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.SECOND_TOKEN_SENDER_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.SYS_ACCOUNT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.SYS_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_RECEIVER;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_TREASURY;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_TREASURY_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_WIPE_KT;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_NODE;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_KT;
import static com.hedera.test.factories.txns.SignedTxnFactory.MASTER_PAYER;
import static com.hedera.test.factories.txns.SignedTxnFactory.STAKING_FUND;
import static com.hedera.test.factories.txns.SignedTxnFactory.TREASURY_PAYER;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.AccountApprovalForAllAllowance;
import com.hedera.hapi.node.state.token.AccountCryptoAllowance;
import com.hedera.hapi.node.state.token.AccountFungibleTokenAllowance;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.transaction.CustomFee;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.EntityNumPair;
import com.hedera.node.app.service.mono.utils.accessors.PlatformTxnAccessor;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.ReadableAccountStoreImpl;
import com.hedera.node.app.service.token.impl.ReadableTokenStoreImpl;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.spi.fixtures.state.MapReadableKVState;
import com.hedera.node.app.spi.fixtures.state.MapWritableKVState;
import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.pbj.runtime.io.buffer.Bytes;
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
    private static final String ACCOUNTS_KEY = "ACCOUNTS";

    private static final AccountCryptoAllowance CRYPTO_ALLOWANCES = AccountCryptoAllowance.newBuilder()
            .spenderNum(DEFAULT_PAYER.getAccountNum())
            .amount(500L)
            .build();
    private static final AccountFungibleTokenAllowance FUNGIBLE_TOKEN_ALLOWANCES =
            AccountFungibleTokenAllowance.newBuilder()
                    .tokenNum(KNOWN_TOKEN_NO_SPECIAL_KEYS.getTokenNum())
                    .spenderNum(DEFAULT_PAYER.getAccountNum())
                    .amount(10_000L)
                    .build();

    private static final AccountApprovalForAllAllowance NFT_ALLOWANCES = AccountApprovalForAllAllowance.newBuilder()
            .tokenNum(KNOWN_TOKEN_WITH_WIPE.getTokenNum())
            .spenderNum(DEFAULT_PAYER.getAccountNum())
            .build();

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
                        toPbj(KNOWN_TOKEN_WITH_WIPE),
                        toPbj(DELETED_TOKEN))
                .forEach(id -> destination.put(EntityNum.fromLong(id.tokenNum()), asToken(source.get(fromPbj(id)))));
        final var wrappedState = new MapReadableKVState<>("TOKENS", destination);
        final var state = new StateKeyAdapter<>(wrappedState, Function.identity());
        return new ReadableTokenStoreImpl(mockStates(Map.of(TOKENS_KEY, state)));
    }

    public static WritableTokenRelationStore wellKnownTokenRelStoreAt() {
        final var miscAcctNum = MISC_ACCOUNT.getAccountNum();
        final var destination = new HashMap<EntityNumPair, TokenRelation>();
        destination.put(
                EntityNumPair.fromLongs(miscAcctNum, KNOWN_TOKEN_IMMUTABLE.getTokenNum()),
                TokenRelation.newBuilder()
                        .accountNumber(miscAcctNum)
                        .tokenNumber(KNOWN_TOKEN_IMMUTABLE.getTokenNum())
                        .balance(10)
                        .build());
        destination.put(
                EntityNumPair.fromLongs(miscAcctNum, KNOWN_TOKEN_NO_SPECIAL_KEYS.getTokenNum()),
                TokenRelation.newBuilder()
                        .accountNumber(miscAcctNum)
                        .tokenNumber(KNOWN_TOKEN_NO_SPECIAL_KEYS.getTokenNum())
                        .balance(20)
                        .build());
        destination.put(
                EntityNumPair.fromLongs(miscAcctNum, KNOWN_TOKEN_WITH_KYC.getTokenNum()),
                TokenRelation.newBuilder()
                        .accountNumber(miscAcctNum)
                        .tokenNumber(KNOWN_TOKEN_WITH_KYC.getTokenNum())
                        .balance(30)
                        .build());

        final var wrappedState = new MapWritableKVState<>(TOKEN_RELS_KEY, destination);
        return new WritableTokenRelationStore(mockWritableStates(Map.of(TOKEN_RELS_KEY, wrappedState)));
    }

    public static ReadableAccountStoreImpl wellKnownAccountStoreAt() {
        return new ReadableAccountStoreImpl(
                mockStates(Map.of(ACCOUNTS_KEY, wrappedAccountState(), ALIASES_KEY, wellKnownAliasState())));
    }

    public static WritableAccountStore wellKnownWritableAccountStoreAt() {
        return new WritableAccountStore(
                mockWritableStates(Map.of(ACCOUNTS_KEY, wrappedAccountState(), ALIASES_KEY, wellKnownAliasState())));
    }

    private static WritableKVState<AccountID, Account> wrappedAccountState() {
        final var destination = new HashMap<AccountID, Account>();
        destination.put(
                toPbj(FIRST_TOKEN_SENDER),
                toPbjAccount(FIRST_TOKEN_SENDER.getAccountNum(), FIRST_TOKEN_SENDER_KT.asPbjKey(), 10_000L));
        destination.put(
                toPbj(SECOND_TOKEN_SENDER),
                toPbjAccount(SECOND_TOKEN_SENDER.getAccountNum(), SECOND_TOKEN_SENDER_KT.asPbjKey(), 10_000L));
        destination.put(
                toPbj(TOKEN_RECEIVER), toPbjAccount(TOKEN_RECEIVER.getAccountNum(), TOKEN_WIPE_KT.asPbjKey(), 0L));
        destination.put(
                toPbj(DEFAULT_NODE), toPbjAccount(DEFAULT_NODE.getAccountNum(), DEFAULT_PAYER_KT.asPbjKey(), 0L));
        destination.put(
                toPbj(DEFAULT_PAYER),
                toPbjAccount(DEFAULT_PAYER.getAccountNum(), DEFAULT_PAYER_KT.asPbjKey(), DEFAULT_PAYER_BALANCE));
        destination.put(
                toPbj(STAKING_FUND), toPbjAccount(STAKING_FUND.getAccountNum(), toPbj(asKeyUnchecked(EMPTY_KEY)), 0L));
        destination.put(
                toPbj(MASTER_PAYER),
                toPbjAccount(MASTER_PAYER.getAccountNum(), DEFAULT_PAYER_KT.asPbjKey(), DEFAULT_PAYER_BALANCE));
        destination.put(
                toPbj(TREASURY_PAYER),
                toPbjAccount(TREASURY_PAYER.getAccountNum(), DEFAULT_PAYER_KT.asPbjKey(), DEFAULT_PAYER_BALANCE));
        destination.put(
                toPbj(NO_RECEIVER_SIG),
                toPbjAccount(NO_RECEIVER_SIG.getAccountNum(), NO_RECEIVER_SIG_KT.asPbjKey(), DEFAULT_BALANCE));
        destination.put(
                toPbj(RECEIVER_SIG),
                toPbjAccount(RECEIVER_SIG.getAccountNum(), RECEIVER_SIG_KT.asPbjKey(), DEFAULT_BALANCE, true));
        destination.put(
                toPbj(SYS_ACCOUNT),
                toPbjAccount(SYS_ACCOUNT.getAccountNum(), SYS_ACCOUNT_KT.asPbjKey(), DEFAULT_BALANCE));
        destination.put(
                toPbj(MISC_ACCOUNT),
                toPbjAccount(MISC_ACCOUNT.getAccountNum(), MISC_ACCOUNT_KT.asPbjKey(), DEFAULT_BALANCE));
        destination.put(
                toPbj(CUSTOM_PAYER_ACCOUNT),
                toPbjAccount(
                        CUSTOM_PAYER_ACCOUNT.getAccountNum(), CUSTOM_PAYER_ACCOUNT_KT.asPbjKey(), DEFAULT_BALANCE));
        destination.put(
                toPbj(OWNER_ACCOUNT),
                toPbjAccount(
                        OWNER_ACCOUNT.getAccountNum(),
                        OWNER_ACCOUNT_KT.asPbjKey(),
                        DEFAULT_BALANCE,
                        false,
                        List.of(CRYPTO_ALLOWANCES),
                        List.of(FUNGIBLE_TOKEN_ALLOWANCES),
                        List.of(NFT_ALLOWANCES)));
        destination.put(
                toPbj(DELEGATING_SPENDER),
                toPbjAccount(
                        DELEGATING_SPENDER.getAccountNum(),
                        DELEGATING_SPENDER_KT.asPbjKey(),
                        DEFAULT_BALANCE,
                        false,
                        List.of(CRYPTO_ALLOWANCES),
                        List.of(FUNGIBLE_TOKEN_ALLOWANCES),
                        List.of(NFT_ALLOWANCES)));
        destination.put(
                toPbj(COMPLEX_KEY_ACCOUNT),
                toPbjAccount(COMPLEX_KEY_ACCOUNT.getAccountNum(), COMPLEX_KEY_ACCOUNT_KT.asPbjKey(), DEFAULT_BALANCE));
        destination.put(
                toPbj(TOKEN_TREASURY),
                toPbjAccount(TOKEN_TREASURY.getAccountNum(), TOKEN_TREASURY_KT.asPbjKey(), DEFAULT_BALANCE));
        destination.put(
                toPbj(DILIGENT_SIGNING_PAYER),
                toPbjAccount(
                        DILIGENT_SIGNING_PAYER.getAccountNum(), DILIGENT_SIGNING_PAYER_KT.asPbjKey(), DEFAULT_BALANCE));
        destination.put(
                toPbj(FROM_OVERLAP_PAYER),
                toPbjAccount(FROM_OVERLAP_PAYER.getAccountNum(), FROM_OVERLAP_PAYER_KT.asPbjKey(), DEFAULT_BALANCE));
        return new MapWritableKVState<>(ACCOUNTS_KEY, destination);
    }

    private static Account toPbjAccount(final long number, final Key key, long balance) {
        return toPbjAccount(number, key, balance, false, List.of(), List.of(), List.of());
    }

    private static Account toPbjAccount(
            final long number, final Key key, long balance, final boolean isReceiverSigRequired) {
        return toPbjAccount(number, key, balance, isReceiverSigRequired, List.of(), List.of(), List.of());
    }

    private static Account toPbjAccount(
            final long number,
            final Key key,
            long balance,
            boolean receiverSigRequired,
            List<AccountCryptoAllowance> cryptoAllowances,
            List<AccountFungibleTokenAllowance> fungibleTokenAllowances,
            List<AccountApprovalForAllAllowance> nftTokenAllowances) {
        return new Account(
                number,
                Bytes.EMPTY,
                key,
                10_000L,
                balance,
                "test",
                false,
                1_234_567L,
                1_234_567L,
                0L,
                false,
                receiverSigRequired,
                3L,
                2L,
                1L,
                2,
                3,
                3,
                3,
                false,
                3,
                0,
                1_234_5678L,
                2,
                76_000L,
                0,
                cryptoAllowances,
                nftTokenAllowances,
                fungibleTokenAllowances,
                2,
                false,
                null);
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
                !token.supplyKey().isEmpty() ? fromGrpcKey(asKeyUnchecked(token.getSupplyKey())) : Key.DEFAULT,
                !token.feeScheduleKey().isEmpty()
                        ? fromGrpcKey(asKeyUnchecked(token.feeScheduleKey().get()))
                        : Key.DEFAULT,
                !token.pauseKey().isEmpty()
                        ? fromGrpcKey(asKeyUnchecked(token.pauseKey().get()))
                        : Key.DEFAULT,
                token.getLastUsedSerialNumber(),
                token.isDeleted(),
                token.tokenType() == com.hedera.node.app.service.evm.store.tokens.TokenType.FUNGIBLE_COMMON
                        ? com.hedera.hapi.node.base.TokenType.FUNGIBLE_COMMON
                        : com.hedera.hapi.node.base.TokenType.NON_FUNGIBLE_UNIQUE,
                token.supplyType() == com.hedera.node.app.service.mono.state.enums.TokenSupplyType.FINITE
                        ? com.hedera.hapi.node.base.TokenSupplyType.FINITE
                        : com.hedera.hapi.node.base.TokenSupplyType.INFINITE,
                token.autoRenewAccount() == null ? 0 : token.autoRenewAccount().num(),
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
