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

import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static com.hedera.node.app.service.token.impl.test.handlers.util.StateBuilderUtil.ACCOUNTS;
import static com.hedera.node.app.service.token.impl.test.handlers.util.StateBuilderUtil.ALIASES;
import static com.hedera.node.app.service.token.impl.test.util.SigReqAdapterUtils.UNSET_STAKED_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.ThresholdKey;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.token.CryptoAllowance;
import com.hedera.hapi.node.token.TokenAllowance;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.impl.ReadableAccountStoreImpl;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.state.spi.ReadableStates;
import com.swirlds.state.spi.WritableStates;
import com.swirlds.state.test.fixtures.MapReadableKVState;
import com.swirlds.state.test.fixtures.MapWritableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Collections;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
// FUTURE : Remove this and use CryptoTokenHandlerTestBase instead for all classes extending this class

/**
 * Base class for testing Crypto handlers implementations.
 */
@ExtendWith(MockitoExtension.class)
public class CryptoHandlerTestBase {
    private static final Function<String, Key.Builder> KEY_BUILDER =
            value -> Key.newBuilder().ed25519(Bytes.wrap(value.getBytes()));
    private static final String A_NAME = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    private static final String B_NAME = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    private static final String C_NAME = "cccccccccccccccccccccccccccccccc";

    public static final Key A_THRESHOLD_KEY = Key.newBuilder()
            .thresholdKey(ThresholdKey.newBuilder()
                    .threshold(2)
                    .keys(KeyList.newBuilder()
                            .keys(
                                    KEY_BUILDER.apply(A_NAME).build(),
                                    KEY_BUILDER.apply(B_NAME).build(),
                                    KEY_BUILDER.apply(C_NAME).build())
                            .build()))
            .build();
    public static final Key A_KEY_LIST = Key.newBuilder()
            .keyList(KeyList.newBuilder()
                    .keys(
                            KEY_BUILDER.apply(A_NAME).build(),
                            KEY_BUILDER.apply(B_NAME).build(),
                            KEY_BUILDER.apply(C_NAME).build()))
            .build();
    public static final Key A_COMPLEX_KEY = Key.newBuilder()
            .thresholdKey(ThresholdKey.newBuilder()
                    .threshold(2)
                    .keys(KeyList.newBuilder()
                            .keys(
                                    KEY_BUILDER.apply(A_NAME).build(),
                                    KEY_BUILDER.apply(B_NAME).build(),
                                    A_THRESHOLD_KEY)))
            .build();
    public static final Key B_COMPLEX_KEY = Key.newBuilder()
            .thresholdKey(ThresholdKey.newBuilder()
                    .threshold(2)
                    .keys(KeyList.newBuilder()
                            .keys(
                                    KEY_BUILDER.apply(A_NAME).build(),
                                    KEY_BUILDER.apply(B_NAME).build(),
                                    A_COMPLEX_KEY)))
            .build();
    public static final Key C_COMPLEX_KEY = Key.newBuilder()
            .thresholdKey(ThresholdKey.newBuilder()
                    .threshold(2)
                    .keys(KeyList.newBuilder()
                            .keys(
                                    KEY_BUILDER.apply(A_NAME).build(),
                                    KEY_BUILDER.apply(B_NAME).build(),
                                    B_COMPLEX_KEY)))
            .build();
    protected final Key key = A_COMPLEX_KEY;
    protected final Key otherKey = C_COMPLEX_KEY;
    protected final AccountID id = AccountID.newBuilder().accountNum(3).build();
    protected final AccountID invalidId =
            AccountID.newBuilder().accountNum(Long.MAX_VALUE).build();
    protected final Timestamp consensusTimestamp =
            Timestamp.newBuilder().seconds(1_234_567L).build();
    protected final Instant consensusInstant = Instant.ofEpochSecond(consensusTimestamp.seconds());
    protected final Key accountKey = A_COMPLEX_KEY;
    protected final Long accountNum = id.accountNumOrThrow();

    protected static final Key aPrimitiveKey = Key.newBuilder()
            .ed25519(Bytes.wrap("01234567890123456789012345678901"))
            .build();
    protected static final ProtoBytes edKeyAlias = new ProtoBytes(aPrimitiveKey.ed25519());
    protected final AccountID alias =
            AccountID.newBuilder().alias(edKeyAlias.value()).build();
    protected final byte[] evmAddress = CommonUtils.unhex("6aea3773ea468a814d954e6dec795bfee7d76e26");
    protected final ContractID contractAlias =
            ContractID.newBuilder().evmAddress(Bytes.wrap(evmAddress)).build();
    /*Contracts */
    protected final ContractID contract =
            ContractID.newBuilder().contractNum(1234).build();
    protected final AccountID deleteAccountId =
            AccountID.newBuilder().accountNum(3213).build();
    protected final AccountID transferAccountId =
            AccountID.newBuilder().accountNum(32134).build();
    protected final Long deleteAccountNum = deleteAccountId.accountNum();
    protected final Long transferAccountNum = transferAccountId.accountNum();

    protected final TokenID nft = TokenID.newBuilder().tokenNum(56789).build();
    protected final TokenID token = TokenID.newBuilder().tokenNum(6789).build();
    protected final AccountID spender = AccountID.newBuilder().accountNum(12345).build();
    protected final AccountID delegatingSpender =
            AccountID.newBuilder().accountNum(1234567).build();
    protected final AccountID owner = AccountID.newBuilder().accountNum(123456).build();
    protected final Key ownerKey = B_COMPLEX_KEY;
    protected final CryptoAllowance cryptoAllowance = CryptoAllowance.newBuilder()
            .spender(spender)
            .owner(owner)
            .amount(10L)
            .build();
    protected final TokenAllowance tokenAllowance = TokenAllowance.newBuilder()
            .spender(spender)
            .amount(10L)
            .tokenId(token)
            .owner(owner)
            .build();
    protected static final long defaultAutoRenewPeriod = 7200000L;
    protected static final long payerBalance = 10_000L;
    protected MapReadableKVState<ProtoBytes, AccountID> readableAliases;

    protected MapReadableKVState<AccountID, Account> readableAccounts;
    protected MapWritableKVState<ProtoBytes, AccountID> writableAliases;
    protected MapWritableKVState<AccountID, Account> writableAccounts;
    protected Account account;
    protected ReadableAccountStore readableStore;
    protected WritableAccountStore writableStore;

    protected Account deleteAccount;

    protected Account transferAccount;

    @Mock
    protected ReadableStates readableStates;

    @Mock(strictness = LENIENT)
    protected WritableStates writableStates;

    @Mock
    private StoreMetricsService storeMetricsService;

    /**
     * Set up the test environment.
     */
    @BeforeEach
    public void setUp() {
        account = givenValidAccount(accountNum);
        deleteAccount = givenValidAccount(deleteAccountNum)
                .copyBuilder()
                .accountId(deleteAccountId)
                .key(accountKey)
                .numberPositiveBalances(0)
                .numberTreasuryTitles(0)
                .build();
        transferAccount = givenValidAccount(transferAccountNum)
                .copyBuilder()
                .accountId(transferAccountId)
                .key(key)
                .build();
        refreshStoresWithCurrentTokenOnlyInReadable();
    }

    protected void basicMetaAssertions(final PreHandleContext context, final int keysSize) {
        assertThat(context.requiredNonPayerKeys()).hasSize(keysSize);
    }

    protected void resetStores() {
        readableAccounts = emptyReadableAccountStateBuilder().build();
        writableAccounts = emptyWritableAccountStateBuilder().build();
        readableAliases = emptyReadableAliasStateBuilder().build();
        writableAliases = emptyWritableAliasStateBuilder().build();
        given(readableStates.<AccountID, Account>get(ACCOUNTS)).willReturn(readableAccounts);
        given(readableStates.<ProtoBytes, AccountID>get(ALIASES)).willReturn(readableAliases);
        given(writableStates.<AccountID, Account>get(ACCOUNTS)).willReturn(writableAccounts);
        given(writableStates.<ProtoBytes, AccountID>get(ALIASES)).willReturn(writableAliases);
        readableStore = new ReadableAccountStoreImpl(readableStates);
        final var configuration = HederaTestConfigBuilder.createConfig();
        writableStore = new WritableAccountStore(writableStates, configuration, storeMetricsService);
    }

    protected void refreshStoresWithCurrentTokenOnlyInReadable() {
        readableAccounts = readableAccountState();
        writableAccounts = emptyWritableAccountStateBuilder().build();
        readableAliases = readableAliasState();
        writableAliases = emptyWritableAliasStateBuilder().build();
        given(readableStates.<AccountID, Account>get(ACCOUNTS)).willReturn(readableAccounts);
        given(readableStates.<ProtoBytes, AccountID>get(ALIASES)).willReturn(readableAliases);
        given(writableStates.<AccountID, Account>get(ACCOUNTS)).willReturn(writableAccounts);
        given(writableStates.<ProtoBytes, AccountID>get(ALIASES)).willReturn(writableAliases);
        readableStore = new ReadableAccountStoreImpl(readableStates);
        final var configuration = HederaTestConfigBuilder.createConfig();
        writableStore = new WritableAccountStore(writableStates, configuration, storeMetricsService);
    }

    protected void refreshStoresWithCurrentTokenInWritable() {
        readableAccounts = readableAccountState();
        writableAccounts = writableAccountStateWithOneKey();
        readableAliases = readableAliasState();
        writableAliases = writableAliasesStateWithOneKey();
        given(readableStates.<AccountID, Account>get(ACCOUNTS)).willReturn(readableAccounts);
        given(readableStates.<ProtoBytes, AccountID>get(ALIASES)).willReturn(readableAliases);
        given(writableStates.<AccountID, Account>get(ACCOUNTS)).willReturn(writableAccounts);
        given(writableStates.<ProtoBytes, AccountID>get(ALIASES)).willReturn(writableAliases);
        readableStore = new ReadableAccountStoreImpl(readableStates);
        final var configuration = HederaTestConfigBuilder.createConfig();
        writableStore = new WritableAccountStore(writableStates, configuration, storeMetricsService);
    }

    @NonNull
    protected MapWritableKVState<AccountID, Account> writableAccountStateWithOneKey() {
        return emptyWritableAccountStateBuilder()
                .value(id, account)
                .value(deleteAccountId, deleteAccount)
                .value(transferAccountId, transferAccount)
                .build();
    }

    @NonNull
    protected MapReadableKVState<AccountID, Account> readableAccountState() {
        return emptyReadableAccountStateBuilder()
                .value(id, account)
                .value(deleteAccountId, deleteAccount)
                .value(transferAccountId, transferAccount)
                .build();
    }

    @NonNull
    protected MapWritableKVState<ProtoBytes, AccountID> writableAliasesStateWithOneKey() {
        return emptyWritableAliasStateBuilder()
                .value(new ProtoBytes(alias.alias()), asAccount(accountNum))
                .value(new ProtoBytes(contractAlias.evmAddress()), asAccount(contract.contractNum()))
                .build();
    }

    @NonNull
    protected MapReadableKVState<ProtoBytes, AccountID> readableAliasState() {
        return emptyReadableAliasStateBuilder()
                .value(new ProtoBytes(alias.alias()), asAccount(accountNum))
                .value(new ProtoBytes(contractAlias.evmAddress()), asAccount(contract.contractNum()))
                .build();
    }

    @NonNull
    protected MapReadableKVState.Builder<AccountID, Account> emptyReadableAccountStateBuilder() {
        return MapReadableKVState.builder(ACCOUNTS);
    }

    @NonNull
    protected MapWritableKVState.Builder<AccountID, Account> emptyWritableAccountStateBuilder() {
        return MapWritableKVState.builder(ACCOUNTS);
    }

    @NonNull
    protected MapWritableKVState.Builder<ProtoBytes, AccountID> emptyWritableAliasStateBuilder() {
        return MapWritableKVState.builder(ALIASES);
    }

    @NonNull
    protected MapReadableKVState.Builder<ProtoBytes, AccountID> emptyReadableAliasStateBuilder() {
        return MapReadableKVState.builder(ALIASES);
    }

    protected Account givenValidAccount(final long accountNum) {
        return new Account(
                AccountID.newBuilder().accountNum(accountNum).build(),
                alias.alias(),
                key,
                1_234_567L,
                payerBalance,
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
                2,
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

    protected void givenValidContract() {
        account = new Account(
                AccountID.newBuilder().accountNum(accountNum).build(),
                alias.alias(),
                key,
                1_234_567L,
                payerBalance,
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
                true,
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

    protected AccountID accountID(final long num) {
        return AccountID.newBuilder().accountNum(num).build();
    }
}
