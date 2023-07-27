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

package com.hedera.node.app.service.token.impl.test.api;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.contract.ContractNonceInfo;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.token.impl.TokenServiceImpl;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.api.TokenServiceApiImpl;
import com.hedera.node.app.spi.fixtures.state.MapWritableKVState;
import com.hedera.node.app.spi.fixtures.state.MapWritableStates;
import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.node.app.spi.state.WritableKVStateBase;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenServiceApiImplTest {
    private static final Configuration DEFAULT_CONFIG = HederaTestConfigBuilder.createConfig();
    private static final Bytes EVM_ADDRESS = Bytes.fromHex("89abcdef89abcdef89abcdef89abcdef89abcdef");

    public static final ContractID CONTRACT_ID_BY_NUM =
            ContractID.newBuilder().contractNum(666).build();
    public static final ContractID OTHER_CONTRACT_ID_BY_NUM =
            ContractID.newBuilder().contractNum(777).build();
    public static final AccountID CONTRACT_ID_BY_ALIAS =
            AccountID.newBuilder().alias(EVM_ADDRESS).build();
    public static final AccountID EOA_ACCOUNT_ID =
            AccountID.newBuilder().accountNum(888).build();
    public static final AccountID CONTRACT_ACCOUNT_ID = AccountID.newBuilder()
            .accountNum(CONTRACT_ID_BY_NUM.contractNumOrThrow())
            .build();
    public static final AccountID OTHER_CONTRACT_ACCOUNT_ID = AccountID.newBuilder()
            .accountNum(OTHER_CONTRACT_ID_BY_NUM.contractNumOrThrow())
            .build();

    private final WritableKVState<Bytes, AccountID> aliasesState =
            new MapWritableKVState<>(TokenServiceImpl.ALIASES_KEY);
    private final WritableKVState<AccountID, Account> accountState =
            new MapWritableKVState<>(TokenServiceImpl.ACCOUNTS_KEY);
    private final WritableStates writableStates = new MapWritableStates(Map.of(
            TokenServiceImpl.ACCOUNTS_KEY, accountState,
            TokenServiceImpl.ALIASES_KEY, aliasesState));
    private final WritableAccountStore accountStore = new WritableAccountStore(writableStates);

    private TokenServiceApiImpl subject;

    @BeforeEach
    void setUp() {
        subject = new TokenServiceApiImpl(DEFAULT_CONFIG, writableStates);
    }

    @Test
    void createsExpectedContractWithAliasIfSet() {
        accountStore.put(Account.newBuilder().accountId(CONTRACT_ACCOUNT_ID).build());

        assertNull(accountStore.getContractById(CONTRACT_ID_BY_NUM));
        subject.markNewlyCreatedAsContract(CONTRACT_ACCOUNT_ID);

        assertEquals(1, accountStore.sizeOfAccountState());
        assertNotNull(accountStore.getContractById(CONTRACT_ID_BY_NUM));
    }

    @Test
    void throwsIseIfAccountNotNewlyCreated() {
        assertThrows(IllegalArgumentException.class, () -> subject.markNewlyCreatedAsContract(CONTRACT_ACCOUNT_ID));
    }

    @Test
    void removesByNumberIfSet() {
        accountStore.put(Account.newBuilder()
                .accountId(AccountID.newBuilder().accountNum(CONTRACT_ID_BY_NUM.contractNumOrThrow()))
                .smartContract(true)
                .build());

        subject.deleteAndMaybeUnaliasContract(CONTRACT_ACCOUNT_ID);

        assertEquals(0, accountStore.sizeOfAccountState());
    }

    @Test
    void removesByAliasIfSet() {
        accountStore.put(Account.newBuilder()
                .accountId(AccountID.newBuilder().accountNum(CONTRACT_ID_BY_NUM.contractNumOrThrow()))
                .alias(EVM_ADDRESS)
                .smartContract(true)
                .build());
        accountStore.putAlias(EVM_ADDRESS, CONTRACT_ACCOUNT_ID);

        subject.deleteAndMaybeUnaliasContract(CONTRACT_ID_BY_ALIAS);

        assertEquals(0, accountStore.sizeOfAccountState());
        assertEquals(0, accountStore.sizeOfAliasesState());
    }

    @Test
    void noopIfAliasReferencesNothing() {
        accountStore.put(Account.newBuilder()
                .accountId(AccountID.newBuilder().accountNum(CONTRACT_ID_BY_NUM.contractNumOrThrow()))
                .smartContract(true)
                .build());

        assertDoesNotThrow(() -> subject.deleteAndMaybeUnaliasContract(CONTRACT_ID_BY_ALIAS));

        assertEquals(1, accountStore.sizeOfAccountState());
    }

    @Test
    void returnsModifiedKeys() {
        accountStore.put(Account.newBuilder()
                .accountId(AccountID.newBuilder().accountNum(CONTRACT_ID_BY_NUM.contractNumOrThrow()))
                .smartContract(true)
                .build());
        accountStore.put(Account.newBuilder()
                .accountId(AccountID.newBuilder().accountNum(OTHER_CONTRACT_ID_BY_NUM.contractNumOrThrow()))
                .smartContract(true)
                .build());

        final var modifiedIdSet = subject.modifiedAccountIds();
        assertEquals(Set.of(CONTRACT_ACCOUNT_ID, OTHER_CONTRACT_ACCOUNT_ID), modifiedIdSet);
    }

    @Test
    void returnsUpdatedNonces() {
        accountStore.put(Account.newBuilder()
                .accountId(CONTRACT_ACCOUNT_ID)
                .ethereumNonce(123L)
                .smartContract(true)
                .build());
        ((WritableKVStateBase<?, ?>) accountState).commit();
        accountStore.put(Account.newBuilder()
                .accountId(CONTRACT_ACCOUNT_ID)
                .ethereumNonce(124L)
                .smartContract(true)
                .build());
        final var updatedNonces = subject.updatedContractNonces();
        assertEquals(List.of(new ContractNonceInfo(CONTRACT_ID_BY_NUM, 124L)), updatedNonces);
    }

    @Test
    void transfersRequestedValue() {
        accountStore.put(Account.newBuilder()
                .accountId(CONTRACT_ACCOUNT_ID)
                .smartContract(true)
                .build());
        accountStore.put(Account.newBuilder()
                .tinybarBalance(123L)
                .accountId(EOA_ACCOUNT_ID)
                .smartContract(true)
                .build());

        subject.transferFromTo(EOA_ACCOUNT_ID, CONTRACT_ACCOUNT_ID, 23L);

        final var postTransferSender = requireNonNull(accountState.get(EOA_ACCOUNT_ID));
        final var postTransferReceiver = requireNonNull(accountState.get(CONTRACT_ACCOUNT_ID));
        assertEquals(100L, postTransferSender.tinybarBalance());
        assertEquals(23L, postTransferReceiver.tinybarBalance());
    }

    @Test
    void refusesToTransferNegativeAmount() {
        assertThrows(
                IllegalArgumentException.class, () -> subject.transferFromTo(EOA_ACCOUNT_ID, CONTRACT_ACCOUNT_ID, -1L));
    }

    @Test
    void refusesToSetSenderBalanceNegative() {
        accountStore.put(Account.newBuilder()
                .accountId(CONTRACT_ACCOUNT_ID)
                .smartContract(true)
                .build());
        accountStore.put(Account.newBuilder()
                .tinybarBalance(123L)
                .accountId(EOA_ACCOUNT_ID)
                .smartContract(true)
                .build());

        assertThrows(
                IllegalArgumentException.class,
                () -> subject.transferFromTo(EOA_ACCOUNT_ID, CONTRACT_ACCOUNT_ID, 124L));
    }

    @Test
    void refusesToOverflowReceiverBalance() {
        accountStore.put(Account.newBuilder()
                .accountId(CONTRACT_ACCOUNT_ID)
                .tinybarBalance(Long.MAX_VALUE)
                .smartContract(true)
                .build());
        accountStore.put(Account.newBuilder()
                .tinybarBalance(1L)
                .accountId(EOA_ACCOUNT_ID)
                .smartContract(true)
                .build());

        assertThrows(
                IllegalArgumentException.class,
                () -> subject.transferFromTo(EOA_ACCOUNT_ID, CONTRACT_ACCOUNT_ID, Long.MAX_VALUE));
    }

    @Test
    void updatesSenderAccountNonce() {
        accountStore.put(Account.newBuilder()
                .accountId(EOA_ACCOUNT_ID)
                .ethereumNonce(123L)
                .build());
        subject.incrementSenderNonce(EOA_ACCOUNT_ID);
        final var postIncrementAccount = requireNonNull(accountState.get(EOA_ACCOUNT_ID));
        assertEquals(124L, postIncrementAccount.ethereumNonce());
    }

    @Test
    void updatesContractAccountNonce() {
        accountStore.put(Account.newBuilder()
                .accountId(CONTRACT_ACCOUNT_ID)
                .smartContract(true)
                .ethereumNonce(123L)
                .build());
        subject.incrementParentNonce(CONTRACT_ID_BY_NUM);
        final var postIncrementAccount = requireNonNull(accountState.get(CONTRACT_ACCOUNT_ID));
        assertEquals(124L, postIncrementAccount.ethereumNonce());
    }
}
