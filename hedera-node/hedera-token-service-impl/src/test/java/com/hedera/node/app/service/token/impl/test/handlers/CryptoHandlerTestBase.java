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
import static com.hedera.test.utils.KeyUtils.A_COMPLEX_KEY;
import static com.hedera.test.utils.KeyUtils.B_COMPLEX_KEY;
import static com.hedera.test.utils.KeyUtils.C_COMPLEX_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.token.CryptoAllowance;
import com.hedera.hapi.node.token.TokenAllowance;
import com.hedera.node.app.service.mono.state.virtual.EntityNumValue;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.token.impl.CryptoSignatureWaiversImpl;
import com.hedera.node.app.service.token.impl.ReadableAccountStore;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.spi.fixtures.state.MapReadableKVState;
import com.hedera.node.app.spi.fixtures.state.MapWritableKVState;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.utility.CommonUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CryptoHandlerTestBase {
    protected static final String ACCOUNTS = "ACCOUNTS";
    protected static final String ALIASES = "ALIASES";
    protected final Key key = A_COMPLEX_KEY;
    protected final Key otherKey = C_COMPLEX_KEY;
    protected final AccountID id = AccountID.newBuilder().accountNum(3).build();
    protected final Timestamp consensusTimestamp =
            Timestamp.newBuilder().seconds(1_234_567L).build();
    protected final Instant consensusInstant = Instant.ofEpochSecond(consensusTimestamp.seconds());
    protected final Key accountKey = A_COMPLEX_KEY;
    protected final HederaKey accountHederaKey = asHederaKey(accountKey).get();
    protected final Long accountNum = id.accountNum();
    protected final EntityNumVirtualKey accountEntityNumVirtualKey = new EntityNumVirtualKey(accountNum);
    private final AccountID alias =
            AccountID.newBuilder().alias(Bytes.wrap("testAlias")).build();
    protected final byte[] evmAddress = CommonUtils.unhex("6aea3773ea468a814d954e6dec795bfee7d76e25");
    protected final ContractID contractAlias =
            ContractID.newBuilder().evmAddress(Bytes.wrap(evmAddress)).build();
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
    protected static final long defaultAutoRenewPeriod = 720000L;
    protected static final long payerBalance = 10_000L;
    protected MapReadableKVState<String, EntityNumValue> readableAliases;
    protected MapReadableKVState<EntityNumVirtualKey, Account> readableAccounts;
    protected MapWritableKVState<String, EntityNumValue> writableAliases;
    protected MapWritableKVState<EntityNumVirtualKey, Account> writableAccounts;
    protected Account account;
    protected ReadableAccountStore readableStore;
    protected WritableAccountStore writableStore;

    @Mock
    protected Account deleteAccount;

    @Mock
    protected Account transferAccount;

    @Mock
    protected ReadableStates readableStates;

    @Mock
    protected WritableStates writableStates;

    @Mock
    protected CryptoSignatureWaiversImpl waivers;

    @BeforeEach
    protected void setUp() {
        givenValidAccount();
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
        given(readableStates.<EntityNumVirtualKey, Account>get(ACCOUNTS)).willReturn(readableAccounts);
        given(readableStates.<String, EntityNumValue>get(ALIASES)).willReturn(readableAliases);
        given(writableStates.<EntityNumVirtualKey, Account>get(ACCOUNTS)).willReturn(writableAccounts);
        given(writableStates.<String, EntityNumValue>get(ALIASES)).willReturn(writableAliases);
        readableStore = new ReadableAccountStore(readableStates);
        writableStore = new WritableAccountStore(writableStates);
    }

    protected void refreshStoresWithCurrentTokenOnlyInReadable() {
        readableAccounts = readableAccountState();
        writableAccounts = emptyWritableAccountStateBuilder().build();
        readableAliases = readableAliasState();
        writableAliases = emptyWritableAliasStateBuilder().build();
        given(readableStates.<EntityNumVirtualKey, Account>get(ACCOUNTS)).willReturn(readableAccounts);
        given(readableStates.<String, EntityNumValue>get(ALIASES)).willReturn(readableAliases);
        readableStore = new ReadableAccountStore(readableStates);
        writableStore = new WritableAccountStore(writableStates);
    }

    protected void refreshStoresWithCurrentTokenInWritable() {
        readableAccounts = readableAccountState();
        writableAccounts = writableAccountStateWithOneKey();
        readableAliases = readableAliasState();
        writableAliases = writableAliasesStateWithOneKey();
        given(readableStates.<EntityNumVirtualKey, Account>get(ACCOUNTS)).willReturn(readableAccounts);
        given(readableStates.<String, EntityNumValue>get(ALIASES)).willReturn(readableAliases);
        given(writableStates.<EntityNumVirtualKey, Account>get(ACCOUNTS)).willReturn(writableAccounts);
        given(writableStates.<String, EntityNumValue>get(ALIASES)).willReturn(writableAliases);
        readableStore = new ReadableAccountStore(readableStates);
        writableStore = new WritableAccountStore(writableStates);
    }

    @NonNull
    protected MapWritableKVState<EntityNumVirtualKey, Account> writableAccountStateWithOneKey() {
        return emptyWritableAccountStateBuilder()
                .value(accountEntityNumVirtualKey, account)
                .value(EntityNumVirtualKey.fromLong(deleteAccountNum), deleteAccount)
                .value(EntityNumVirtualKey.fromLong(transferAccountNum), transferAccount)
                .build();
    }

    @NonNull
    protected MapReadableKVState<EntityNumVirtualKey, Account> readableAccountState() {
        return emptyReadableAccountStateBuilder()
                .value(accountEntityNumVirtualKey, account)
                .value(EntityNumVirtualKey.fromLong(deleteAccountNum), deleteAccount)
                .value(EntityNumVirtualKey.fromLong(transferAccountNum), transferAccount)
                .build();
    }

    @NonNull
    protected MapWritableKVState<String, EntityNumValue> writableAliasesStateWithOneKey() {
        return emptyWritableAliasStateBuilder()
                .value(alias.toString(), new EntityNumValue(accountNum))
                .value(contractAlias.toString(), new EntityNumValue(contract.contractNum()))
                .build();
    }

    @NonNull
    protected MapReadableKVState<String, EntityNumValue> readableAliasState() {
        return emptyReadableAliasStateBuilder()
                .value(alias.toString(), new EntityNumValue(accountNum))
                .value(contractAlias.toString(), new EntityNumValue(contract.contractNum()))
                .build();
    }

    @NonNull
    protected MapReadableKVState.Builder<EntityNumVirtualKey, Account> emptyReadableAccountStateBuilder() {
        return MapReadableKVState.builder(ACCOUNTS);
    }

    @NonNull
    protected MapWritableKVState.Builder<EntityNumVirtualKey, Account> emptyWritableAccountStateBuilder() {
        return MapWritableKVState.builder(ACCOUNTS);
    }

    @NonNull
    protected MapWritableKVState.Builder<String, EntityNumValue> emptyWritableAliasStateBuilder() {
        return MapWritableKVState.builder(ALIASES);
    }

    @NonNull
    protected MapReadableKVState.Builder<String, EntityNumValue> emptyReadableAliasStateBuilder() {
        return MapReadableKVState.builder(ALIASES);
    }

    protected void givenValidAccount() {
        account = new Account(
                accountNum,
                alias.alias(),
                key,
                1_234_567L,
                payerBalance,
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
                false,
                null);
    }
}
