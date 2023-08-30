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

package com.hedera.node.app.service.token.impl.test.schemas;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.token.impl.TokenServiceImpl;
import com.hedera.node.app.service.token.impl.schemas.GenesisSchema;
import com.hedera.node.app.spi.fixtures.info.FakeNetworkInfo;
import com.hedera.node.app.spi.fixtures.state.MapWritableKVState;
import com.hedera.node.app.spi.fixtures.state.MapWritableStates;
import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.node.app.spi.state.EmptyReadableStates;
import com.hedera.node.app.spi.state.WritableSingletonStateBase;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.node.app.state.merkle.MigrationContextImpl;
import com.hedera.node.config.data.BootstrapConfig;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class GenesisSchemaTest {

    private final String genesisKey = "0123456789012345678901234567890123456789012345678901234567890123";
    private final long expirationSec = 100;
    private MapWritableKVState<AccountID, Account> accounts;
    private WritableStates newStates;
    private Configuration config;
    private NetworkInfo networkInfo;

    @BeforeEach
    void setUp() {
        accounts = MapWritableKVState.<AccountID, Account>builder(TokenServiceImpl.ACCOUNTS_KEY)
                .build();

        newStates = MapWritableStates.builder()
                .state(accounts)
                .state(MapWritableKVState.builder(TokenServiceImpl.STAKING_INFO_KEY)
                        .build())
                .state(new WritableSingletonStateBase<>(
                        TokenServiceImpl.STAKING_NETWORK_REWARDS_KEY, () -> null, c -> {}))
                .build();

        networkInfo = new FakeNetworkInfo();

        config = HederaTestConfigBuilder.create()
                .withConfigDataType(HederaConfig.class)
                .withConfigDataType(LedgerConfig.class)
                .withConfigDataType(BootstrapConfig.class)
                .withValue("bootstrap.genesisPublicKey", genesisKey)
                .withValue("bootstrap.system.entityExpiry", expirationSec)
                .getOrCreateConfig();
    }

    @Test
    void systemAccountsCreated() {
        final var schema = new GenesisSchema();
        schema.migrate(new MigrationContextImpl(EmptyReadableStates.INSTANCE, newStates, config, networkInfo));

        for (int i = 1; i <= 100; i++) {
            final var account = accounts.get(accountID(i));
            assertThat(account).isNotNull();
            assertThat(account.accountId()).isEqualTo(accountID(i));

            final var balance = i == 2 ? 100_000_000L * 50_000_000_000L : 0L;
            assertThat(account.keyOrThrow().ed25519OrThrow().toHex()).isEqualTo(genesisKey);
            assertBasicAccount(account, balance, expirationSec);
        }
    }

    @Test
    void fileEntityIdsNotUsed() {
        final var schema = new GenesisSchema();
        schema.migrate(new MigrationContextImpl(EmptyReadableStates.INSTANCE, newStates, config, networkInfo));

        for (int i = 101; i < 200; i++) {
            assertThat(accounts.contains(accountID(i))).isFalse();
        }
    }

    @Test
    void accountsBetweenFilesAndContracts() {
        final var schema = new GenesisSchema();
        schema.migrate(new MigrationContextImpl(EmptyReadableStates.INSTANCE, newStates, config, networkInfo));

        for (int i = 200; i < 350; i++) {
            final var account = accounts.get(accountID(i));
            assertThat(account).isNotNull();
            assertThat(account.accountId()).isEqualTo(accountID(i));
            assertThat(account.keyOrThrow().ed25519OrThrow().toHex()).isEqualTo(genesisKey);
            assertBasicAccount(account, 0, expirationSec);
        }
    }

    @Test
    void contractEntityIdsNotUsed() {
        final var schema = new GenesisSchema();
        schema.migrate(new MigrationContextImpl(EmptyReadableStates.INSTANCE, newStates, config, networkInfo));

        for (int i = 350; i < 400; i++) {
            assertThat(accounts.contains(accountID(i))).isFalse();
        }
    }

    @Test
    void accountsAfterContracts() {
        final var schema = new GenesisSchema();
        schema.migrate(new MigrationContextImpl(EmptyReadableStates.INSTANCE, newStates, config, networkInfo));

        for (int i = 400; i <= 750; i++) {
            final var account = accounts.get(accountID(i));
            assertThat(account).isNotNull();
            assertThat(account.accountId()).isEqualTo(accountID(i));
            assertThat(account.keyOrThrow().ed25519OrThrow().toHex()).isEqualTo(genesisKey);
            assertBasicAccount(account, 0, expirationSec);
        }
    }

    @Test
    void entityIdsBetweenSystemAccountsAndRewardAccountsAreEmpty() {
        final var schema = new GenesisSchema();
        schema.migrate(new MigrationContextImpl(EmptyReadableStates.INSTANCE, newStates, config, networkInfo));

        for (int i = 751; i < 800; i++) {
            assertThat(accounts.contains(accountID(i))).isFalse();
        }
    }

    @Test
    void stakingRewardAccounts() {
        final var schema = new GenesisSchema();
        schema.migrate(new MigrationContextImpl(EmptyReadableStates.INSTANCE, newStates, config, networkInfo));

        final var stakingRewardAccount = accounts.get(accountID(800));
        assertThat(stakingRewardAccount).isNotNull();
        assertThat(stakingRewardAccount.accountId()).isEqualTo(accountID(800));
        assertBasicAccount(stakingRewardAccount, 0, 33197904000L);

        final var nodeRewardAccount = accounts.get(accountID(801));
        assertThat(nodeRewardAccount).isNotNull();
        assertThat(nodeRewardAccount.accountId()).isEqualTo(accountID(801));
        assertBasicAccount(nodeRewardAccount, 0, 33197904000L);
    }

    @Test
    void entityIdsAfterRewardAccountsAreEmpty() {
        final var schema = new GenesisSchema();
        schema.migrate(new MigrationContextImpl(EmptyReadableStates.INSTANCE, newStates, config, networkInfo));

        for (int i = 802; i < 900; i++) {
            assertThat(accounts.contains(accountID(i))).isFalse();
        }
    }

    @Test
    void specialAccountsAfter900() {
        final var schema = new GenesisSchema();
        schema.migrate(new MigrationContextImpl(EmptyReadableStates.INSTANCE, newStates, config, networkInfo));

        for (int i = 900; i <= 1000; i++) {
            final var account = accounts.get(accountID(i));
            assertThat(account).isNotNull();
            assertThat(account.accountId()).isEqualTo(accountID(i));
            assertThat(account.keyOrThrow().ed25519OrThrow().toHex()).isEqualTo(genesisKey);
            assertBasicAccount(account, 0, expirationSec);
        }
    }

    private void assertBasicAccount(Account account, long balance, long expiry) {
        assertThat(account.tinybarBalance()).isEqualTo(balance);
        assertThat(account.alias()).isEqualTo(Bytes.EMPTY);
        assertThat(account.expirationSecond()).isEqualTo(expiry);
        assertThat(account.memo()).isEmpty();
        assertThat(account.deleted()).isFalse();
        assertThat(account.stakedToMe()).isZero();
        assertThat(account.stakePeriodStart()).isZero();
        assertThat(account.stakedId().kind()).isEqualTo(Account.StakedIdOneOfType.UNSET);
        assertThat(account.declineReward()).isTrue();
        assertThat(account.receiverSigRequired()).isFalse();
        assertThat(account.hasHeadNftId()).isFalse();
        assertThat(account.headNftSerialNumber()).isZero();
        assertThat(account.numberOwnedNfts()).isZero();
        assertThat(account.maxAutoAssociations()).isZero();
        assertThat(account.usedAutoAssociations()).isZero();
        assertThat(account.numberAssociations()).isZero();
        assertThat(account.smartContract()).isFalse();
        assertThat(account.numberPositiveBalances()).isZero();
        assertThat(account.ethereumNonce()).isZero();
        assertThat(account.stakeAtStartOfLastRewardedPeriod()).isZero();
        assertThat(account.hasAutoRenewAccountId()).isFalse();
        assertThat(account.autoRenewSeconds()).isEqualTo(expiry);
        assertThat(account.contractKvPairsNumber()).isZero();
        assertThat(account.cryptoAllowances()).isEmpty();
        assertThat(account.approveForAllNftAllowances()).isEmpty();
        assertThat(account.tokenAllowances()).isEmpty();
        assertThat(account.numberTreasuryTitles()).isZero();
        assertThat(account.expiredAndPendingRemoval()).isFalse();
        assertThat(account.firstContractStorageKey()).isEqualTo(Bytes.EMPTY);
    }

    private AccountID accountID(int num) {
        return AccountID.newBuilder().accountNum(num).build();
    }
}
