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

package com.hedera.node.app.service.token.impl.test;

import static com.hedera.node.app.service.token.impl.TokenServiceImpl.ACCOUNTS_KEY;
import static com.hedera.node.app.spi.HapiUtils.EMPTY_KEY_LIST;
import static java.util.Collections.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.token.CryptoCreateTransactionBody;
import com.hedera.node.app.service.token.impl.SystemAccountsInitializer;
import com.hedera.node.app.spi.fixtures.state.MapWritableKVState;
import com.hedera.node.app.spi.fixtures.state.MapWritableStates;
import com.hedera.node.app.spi.state.MigrationContext;
import com.hedera.node.app.spi.workflows.record.GenesisRecordsConsensusHook;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;
import org.assertj.core.api.Assertions;
import org.bouncycastle.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SystemAccountsInitializerTest {
    private static final long EXPECTED_TREASURY_BALANCE = 5000000000000000000L;
    private static final int NUM_SYSTEM_ACCOUNTS = 312;
    private static final long EXPECTED_ENTITY_EXPIRY = 1812637686L;
    private static final long TREASURY_ACCOUNT_NUM = 2L;

    @Mock
    private MigrationContext migrationContext;

    @Mock
    private GenesisRecordsConsensusHook genesisRecordsBuilder;

    @Captor
    private ArgumentCaptor<Map<Account, CryptoCreateTransactionBody.Builder>> sysAcctMapCaptor;

    @Captor
    private ArgumentCaptor<Map<Account, CryptoCreateTransactionBody.Builder>> stakingAcctMapCaptor;

    @Captor
    private ArgumentCaptor<Map<Account, CryptoCreateTransactionBody.Builder>> multiuseAcctMapCaptor;

    @Captor
    private ArgumentCaptor<Map<Account, CryptoCreateTransactionBody.Builder>> treasuryCloneMapCaptor;

    private SystemAccountsInitializer subject;

    @BeforeEach
    void setUp() {
        given(migrationContext.genesisRecordsBuilder()).willReturn(genesisRecordsBuilder);
        subject = new SystemAccountsInitializer();
    }

    @Test
    void createsAllAccounts() {
        given(migrationContext.configuration()).willReturn(buildConfig(NUM_SYSTEM_ACCOUNTS));
        given(migrationContext.newStates())
                .willReturn(new MapWritableStates(Map.of(
                        ACCOUNTS_KEY, new MapWritableKVState<>(ACCOUNTS_KEY, new HashMap<AccountID, Account>()))));

        subject.createSystemAccounts(migrationContext);

        // Verify created system accounts
        verify(genesisRecordsBuilder).systemAccounts(sysAcctMapCaptor.capture());
        final var sysAcctsResult = sysAcctMapCaptor.getValue();
        Assertions.assertThat(sysAcctsResult)
                .isNotNull()
                .hasSize(NUM_SYSTEM_ACCOUNTS)
                .allSatisfy((account, builder) -> {
                    Assertions.assertThat(account).isNotNull();
                    Assertions.assertThat(account.receiverSigRequired()).isFalse();
                    Assertions.assertThat(account.smartContract()).isFalse();
                    Assertions.assertThat(account.memo()).isEmpty();
                    Assertions.assertThat(account.deleted()).isFalse();

                    if (account.accountId().accountNum() == TREASURY_ACCOUNT_NUM) {
                        Assertions.assertThat(account.tinybarBalance()).isEqualTo(EXPECTED_TREASURY_BALANCE);
                    } else {
                        Assertions.assertThat(account.tinybarBalance()).isZero();
                    }
                    Assertions.assertThat(account.expiry()).isEqualTo(EXPECTED_ENTITY_EXPIRY);
                    Assertions.assertThat(account.autoRenewSecs()).isEqualTo(EXPECTED_ENTITY_EXPIRY);
                    Assertions.assertThat(account.key()).isNotNull();

                    verifyCryptoCreateBuilder(account, builder);
                });

        // Verify created staking accounts
        verify(genesisRecordsBuilder).stakingAccounts(stakingAcctMapCaptor.capture());
        final var stakingAcctsResult = stakingAcctMapCaptor.getValue();
        Assertions.assertThat(stakingAcctsResult).isNotNull().hasSize(2).allSatisfy((account, builder) -> {
            Assertions.assertThat(account).isNotNull();
            Assertions.assertThat(account.receiverSigRequired()).isFalse();
            Assertions.assertThat(account.smartContract()).isFalse();
            Assertions.assertThat(account.memo()).isEmpty();
            Assertions.assertThat(account.deleted()).isFalse();
            Assertions.assertThat(account.declineReward()).isFalse();
            Assertions.assertThat(account.alias()).isEqualTo(Bytes.EMPTY);

            Assertions.assertThat(account.key()).isEqualTo(EMPTY_KEY_LIST);
            Assertions.assertThat(account.tinybarBalance()).isZero();
            Assertions.assertThat(account.maxAutoAssociations()).isZero();
            Assertions.assertThat(account.expiry()).isEqualTo(EXPECTED_ENTITY_EXPIRY);

            verifyCryptoCreateBuilder(account, builder);
        });
        Assertions.assertThat(stakingAcctsResult.keySet().stream()
                        .map(Account::accountId)
                        .map(AccountID::accountNum)
                        .toArray())
                .containsExactlyInAnyOrder(800L, 801L);

        // Verify created multipurpose accounts
        verify(genesisRecordsBuilder).multipurposeAccounts(multiuseAcctMapCaptor.capture());
        final var multiuseAcctsResult = multiuseAcctMapCaptor.getValue();
        Assertions.assertThat(multiuseAcctsResult).isNotNull().hasSize(101).allSatisfy((account, builder) -> {
            Assertions.assertThat(account).isNotNull();
            Assertions.assertThat(account.receiverSigRequired()).isFalse();
            Assertions.assertThat(account.smartContract()).isFalse();
            Assertions.assertThat(account.memo()).isEmpty();
            Assertions.assertThat(account.deleted()).isFalse();

            Assertions.assertThat(account.key()).isNotNull();
            Assertions.assertThat(account.tinybarBalance()).isZero();
            Assertions.assertThat(account.expiry()).isEqualTo(EXPECTED_ENTITY_EXPIRY);

            verifyCryptoCreateBuilder(account, builder);
        });

        // Verify created treasury clones
        verify(genesisRecordsBuilder).treasuryClones(treasuryCloneMapCaptor.capture());
        final var treasuryCloneAcctsResult = treasuryCloneMapCaptor.getValue();
        Assertions.assertThat(treasuryCloneAcctsResult).isNotNull().hasSize(388).allSatisfy((account, builder) -> {
            Assertions.assertThat(account).isNotNull();
            Assertions.assertThat(account.receiverSigRequired()).isFalse();
            Assertions.assertThat(account.smartContract()).isFalse();
            Assertions.assertThat(account.memo()).isEmpty();
            Assertions.assertThat(account.deleted()).isFalse();

            Assertions.assertThat(account.key()).isNotNull();
            Assertions.assertThat(account.tinybarBalance()).isZero();
            Assertions.assertThat(account.expiry()).isEqualTo(EXPECTED_ENTITY_EXPIRY);
            Assertions.assertThat(account.autoRenewSecs()).isEqualTo(EXPECTED_ENTITY_EXPIRY);

            verifyCryptoCreateBuilder(account, builder);
        });
    }

    @Test
    void someAccountsAlreadyExist() {
        // We'll only configure 4 system accounts, half of which will already exist
        given(migrationContext.configuration()).willReturn(buildConfig(4));
        final var accts = new HashMap<AccountID, Account>();
        IntStream.rangeClosed(1, 2).forEach(i -> putNewAccount(i, accts));
        // One of the two staking accounts will already exist
        final var stakingAcctId = AccountID.newBuilder().accountNum(800L).build();
        accts.put(stakingAcctId, Account.newBuilder().accountId(stakingAcctId).build());
        // Half of the multipurpose accounts will already exist
        IntStream.rangeClosed(900, 950).forEach(i -> putNewAccount(i, accts));
        // All but five of the treasury clones will already exist
        IntStream.rangeClosed(200, 745).forEach(i -> {
            if (isRegularAcctNum(i)) putNewAccount(i, accts);
        });
        given(migrationContext.newStates())
                .willReturn(new MapWritableStates(Map.of(ACCOUNTS_KEY, new MapWritableKVState<>(ACCOUNTS_KEY, accts))));

        subject.createSystemAccounts(migrationContext);

        verify(genesisRecordsBuilder).systemAccounts(sysAcctMapCaptor.capture());
        final var sysAcctsResult = sysAcctMapCaptor.getValue();
        // Only system accts with IDs 3 and 4 should have been created
        Assertions.assertThat(sysAcctsResult).hasSize(2);

        verify(genesisRecordsBuilder).stakingAccounts(stakingAcctMapCaptor.capture());
        final var stakingAcctsResult = stakingAcctMapCaptor.getValue();
        // Only the staking acct with ID 801 should have been created
        Assertions.assertThat(stakingAcctsResult).hasSize(1);

        verify(genesisRecordsBuilder).multipurposeAccounts(multiuseAcctMapCaptor.capture());
        final var multiuseAcctsResult = multiuseAcctMapCaptor.getValue();
        // Only multi-use accts with IDs 951-1000 should have been created
        Assertions.assertThat(multiuseAcctsResult).hasSize(50);

        verify(genesisRecordsBuilder).treasuryClones(treasuryCloneMapCaptor.capture());
        final var treasuryCloneAcctsResult = treasuryCloneMapCaptor.getValue();
        // Only treasury clones with IDs 746-750 should have been created
        Assertions.assertThat(treasuryCloneAcctsResult).hasSize(5);
    }

    @Test
    void allAccountsAlreadyExist() {
        // All the system accounts will already exist
        given(migrationContext.configuration()).willReturn(buildConfig(NUM_SYSTEM_ACCOUNTS));
        final var accts = new HashMap<AccountID, Account>();
        IntStream.rangeClosed(1, NUM_SYSTEM_ACCOUNTS).forEach(i -> putNewAccount(i, accts));
        // Both of the two staking accounts will already exist
        IntStream.rangeClosed(800, 801).forEach(i -> putNewAccount(i, accts));
        // All the multipurpose accounts will already exist
        IntStream.rangeClosed(900, 1000).forEach(i -> putNewAccount(i, accts));
        // All the treasury clones will already exist
        IntStream.rangeClosed(200, 750).forEach(i -> {
            if (isRegularAcctNum(i)) putNewAccount(i, accts);
        });
        given(migrationContext.newStates())
                .willReturn(new MapWritableStates(Map.of(ACCOUNTS_KEY, new MapWritableKVState<>(ACCOUNTS_KEY, accts))));

        subject.createSystemAccounts(migrationContext);

        verify(genesisRecordsBuilder).systemAccounts(emptyMap());
        verify(genesisRecordsBuilder).stakingAccounts(emptyMap());
        verify(genesisRecordsBuilder).multipurposeAccounts(emptyMap());
        verify(genesisRecordsBuilder).treasuryClones(emptyMap());
    }

    /**
     * Compares the given account (already assumed to be correct) to the given crypto create
     * transaction body builder
     */
    private void verifyCryptoCreateBuilder(
            final Account acctResult, final CryptoCreateTransactionBody.Builder builderSubject) {
        Assertions.assertThat(builderSubject).isNotNull();
        Assertions.assertThat(builderSubject.build())
                .isEqualTo(CryptoCreateTransactionBody.newBuilder()
                        .key(acctResult.key())
                        .memo(acctResult.memo())
                        .declineReward(acctResult.declineReward())
                        .receiverSigRequired(acctResult.receiverSigRequired())
                        .initialBalance(acctResult.tinybarBalance())
                        .autoRenewPeriod(Duration.newBuilder()
                                .seconds(acctResult.autoRenewSecs())
                                .build())
                        .build());
    }

    private void putNewAccount(final long num, final HashMap<AccountID, Account> accts) {
        final var acctId = AccountID.newBuilder().accountNum(num).build();
        final var acct = Account.newBuilder().accountId(acctId).build();
        accts.put(acctId, acct);
    }

    private Configuration buildConfig(int numSystemAccounts) {
        return HederaTestConfigBuilder.create()
                // Accounts Config
                .withValue("accounts.treasury", TREASURY_ACCOUNT_NUM)
                .withValue("accounts.stakingRewardAccount", 800L)
                .withValue("accounts.nodeRewardAccount", 801L)
                // Bootstrap Config
                .withValue(
                        "bootstrap.genesisPublicKey",
                        "0x0aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110e92")
                .withValue("bootstrap.system.entityExpiry", EXPECTED_ENTITY_EXPIRY)
                // Hedera Config
                .withValue("hedera.realm", 0L)
                .withValue("hedera.shard", 0L)
                // Ledger Config
                .withValue("ledger.numSystemAccounts", numSystemAccounts)
                .withValue("ledger.totalTinyBarFloat", EXPECTED_TREASURY_BALANCE)
                .getOrCreateConfig();
    }

    /**
     * @return true if the given account number is NOT a staking account number or system contract
     */
    private boolean isRegularAcctNum(final long i) {
        // Skip the staking account nums
        if (Arrays.contains(new long[] {800, 801}, i)) return false;
        // Skip the system contract account nums
        return i < 350 || i > 399;
    }
}
