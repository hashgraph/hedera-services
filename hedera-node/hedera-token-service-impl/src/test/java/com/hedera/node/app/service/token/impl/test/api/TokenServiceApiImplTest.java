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

import static com.hedera.node.app.service.token.impl.validators.TokenAttributesValidator.IMMUTABILITY_SENTINEL_KEY;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.contract.ContractNonceInfo;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.token.api.ContractChangeSummary;
import com.hedera.node.app.service.token.fixtures.FakeFeeRecordBuilder;
import com.hedera.node.app.service.token.impl.TokenServiceImpl;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.api.TokenServiceApiImpl;
import com.hedera.node.app.service.token.impl.validators.StakingValidator;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.fixtures.state.MapWritableKVState;
import com.hedera.node.app.spi.fixtures.state.MapWritableStates;
import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.node.app.spi.state.WritableKVStateBase;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import com.swirlds.test.framework.config.TestConfigBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenServiceApiImplTest {
    private static final Key STANDIN_CONTRACT_KEY =
            Key.newBuilder().contractID(ContractID.newBuilder().contractNum(0)).build();
    private static final Configuration DEFAULT_CONFIG = HederaTestConfigBuilder.createConfig();
    private static final Bytes EVM_ADDRESS =
            com.hedera.pbj.runtime.io.buffer.Bytes.fromHex("89abcdef89abcdef89abcdef89abcdef89abcdef");
    private static final Bytes OTHER_EVM_ADDRESS = Bytes.fromHex("29abcde089abcde089abcde089abcde089abcde0");
    private static final Bytes SOME_STORE_KEY = com.hedera.pbj.runtime.io.buffer.Bytes.fromHex("0123456789");

    public static final ContractID CONTRACT_ID_BY_NUM =
            ContractID.newBuilder().contractNum(666).build();
    public static final ContractID OTHER_CONTRACT_ID_BY_NUM =
            ContractID.newBuilder().contractNum(777).build();
    public static final ContractID CONTRACT_ID_BY_ALIAS =
            ContractID.newBuilder().evmAddress(EVM_ADDRESS).build();
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

    @Mock
    private StakingValidator stakingValidator;

    @Mock
    private NetworkInfo networkInfo;

    private TokenServiceApiImpl subject;

    @BeforeEach
    void setUp() {
        subject = new TokenServiceApiImpl(DEFAULT_CONFIG, stakingValidator, writableStates);
    }

    @Test
    void delegatesStakingValidationAsExpected() {
        subject.assertValidStakingElection(true, false, "STAKED_NODE_ID", null, 123L, accountStore, networkInfo);

        verify(stakingValidator)
                .validateStakedIdForCreation(true, false, "STAKED_NODE_ID", null, 123L, accountStore, networkInfo);
    }

    @Test
    void canUpdateStorageMetadata() {
        accountStore.put(Account.newBuilder()
                .accountId(CONTRACT_ACCOUNT_ID)
                .contractKvPairsNumber(3)
                .smartContract(true)
                .build());

        subject.updateStorageMetadata(CONTRACT_ACCOUNT_ID, SOME_STORE_KEY, 7);

        final var postIncrementAccount = requireNonNull(accountState.get(CONTRACT_ACCOUNT_ID));
        assertEquals(SOME_STORE_KEY, postIncrementAccount.firstContractStorageKey());
        assertEquals(10, postIncrementAccount.contractKvPairsNumber());
    }

    @Test
    void missingAccountHasZeroOriginalKvUsage() {
        assertEquals(0, subject.originalKvUsageFor(CONTRACT_ACCOUNT_ID));
    }

    @Test
    void extantContractHasOriginalUsage() {
        accountStore.put(Account.newBuilder()
                .accountId(CONTRACT_ACCOUNT_ID)
                .contractKvPairsNumber(3)
                .smartContract(true)
                .build());
        ((WritableKVStateBase<?, ?>) accountState).commit();

        assertEquals(3, subject.originalKvUsageFor(CONTRACT_ACCOUNT_ID));
    }

    @Test
    void refusesToSetNegativeKvPairCount() {
        accountStore.put(Account.newBuilder()
                .accountId(CONTRACT_ACCOUNT_ID)
                .contractKvPairsNumber(3)
                .smartContract(true)
                .build());

        assertThrows(
                IllegalArgumentException.class,
                () -> subject.updateStorageMetadata(CONTRACT_ACCOUNT_ID, SOME_STORE_KEY, -4));
    }

    @Test
    void refusesToUpdateKvCountsForNonContract() {
        accountStore.put(Account.newBuilder()
                .accountId(CONTRACT_ACCOUNT_ID)
                .contractKvPairsNumber(3)
                .build());

        assertThrows(
                IllegalArgumentException.class,
                () -> subject.updateStorageMetadata(CONTRACT_ACCOUNT_ID, SOME_STORE_KEY, -3));
    }

    @Test
    void finalizesHollowAccountAsContractAsExpected() {
        accountStore.put(Account.newBuilder()
                .accountId(CONTRACT_ACCOUNT_ID)
                .key(IMMUTABILITY_SENTINEL_KEY)
                .build());

        subject.finalizeHollowAccountAsContract(CONTRACT_ACCOUNT_ID, 1L);

        assertEquals(1, accountStore.sizeOfAccountState());
        final var finalizedAccount = accountStore.getContractById(CONTRACT_ID_BY_NUM);
        assertNotNull(finalizedAccount);
        assertEquals(STANDIN_CONTRACT_KEY, finalizedAccount.key());
        assertEquals(1L, finalizedAccount.ethereumNonce());
        assertTrue(finalizedAccount.smartContract());
    }

    @Test
    void refusesToFinalizeNonHollowAccountAsContract() {
        accountStore.put(Account.newBuilder()
                .accountId(CONTRACT_ACCOUNT_ID)
                .key(Key.DEFAULT)
                .build());

        assertThrows(
                IllegalArgumentException.class, () -> subject.finalizeHollowAccountAsContract(CONTRACT_ACCOUNT_ID, 1L));
    }

    @Test
    void createsExpectedContractWithAliasIfSet() {
        accountStore.put(Account.newBuilder().accountId(CONTRACT_ACCOUNT_ID).build());

        assertNull(accountStore.getContractById(CONTRACT_ID_BY_NUM));
        subject.markAsContract(CONTRACT_ACCOUNT_ID, null);

        assertEquals(1, accountStore.sizeOfAccountState());
        assertNotNull(accountStore.getContractById(CONTRACT_ID_BY_NUM));
    }

    @Test
    void marksDeletedByNumberIfSet() {
        accountStore.put(Account.newBuilder()
                .accountId(AccountID.newBuilder().accountNum(CONTRACT_ID_BY_NUM.contractNumOrThrow()))
                .smartContract(true)
                .build());

        subject.deleteAndMaybeUnaliasContract(CONTRACT_ID_BY_NUM);

        assertEquals(1, accountStore.sizeOfAccountState());
        final var deletedContract = accountStore.getContractById(CONTRACT_ID_BY_NUM);
        assertTrue(deletedContract.deleted());
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

        assertEquals(1, accountStore.sizeOfAccountState());
        final var deletedContract = accountStore.getContractById(CONTRACT_ID_BY_NUM);
        assertTrue(deletedContract.deleted());
        assertEquals(0, accountStore.sizeOfAliasesState());
    }

    @Test
    void warnsLoudlyButRemovesBothAliasesIfPresent() {
        // This scenario with two aliases referencing the same selfdestruct-ed contract is currently
        // impossible (since only auto-created accounts with ECDSA keys can have two aliases), but if
        // it somehow occurs, we might as well clean up both aliases
        accountStore.put(Account.newBuilder()
                .accountId(AccountID.newBuilder().accountNum(CONTRACT_ID_BY_NUM.contractNumOrThrow()))
                .alias(OTHER_EVM_ADDRESS)
                .smartContract(true)
                .build());
        accountStore.putAlias(EVM_ADDRESS, CONTRACT_ACCOUNT_ID);
        accountStore.putAlias(OTHER_EVM_ADDRESS, CONTRACT_ACCOUNT_ID);

        subject.deleteAndMaybeUnaliasContract(CONTRACT_ID_BY_ALIAS);

        assertEquals(1, accountStore.sizeOfAccountState());
        final var deletedContract = requireNonNull(accountStore.getContractById(CONTRACT_ID_BY_NUM));
        assertTrue(deletedContract.deleted());
        assertEquals(Bytes.EMPTY, deletedContract.alias());
        assertEquals(0, accountStore.sizeOfAliasesState());
    }

    @Test
    void returnsUpdatedNoncesAndCreatedIds() {
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
        accountStore.put(Account.newBuilder()
                .accountId(OTHER_CONTRACT_ACCOUNT_ID)
                .ethereumNonce(1L)
                .smartContract(true)
                .build());
        final var expectedSummary = new ContractChangeSummary(
                new ArrayList<>(List.of(OTHER_CONTRACT_ID_BY_NUM)),
                new ArrayList<>(List.of(
                        new ContractNonceInfo(CONTRACT_ID_BY_NUM, 124L),
                        new ContractNonceInfo(OTHER_CONTRACT_ID_BY_NUM, 1L))));
        final var actualSummary = subject.summarizeContractChanges();
        assertEquals(expectedSummary, actualSummary);
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
                () -> subject.transferFromTo(CONTRACT_ACCOUNT_ID, EOA_ACCOUNT_ID, Long.MAX_VALUE));
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
    void setsAccountNonce() {
        accountStore.put(Account.newBuilder()
                .accountId(EOA_ACCOUNT_ID)
                .ethereumNonce(123L)
                .build());
        subject.setNonce(EOA_ACCOUNT_ID, 321L);
        final var postIncrementAccount = requireNonNull(accountState.get(EOA_ACCOUNT_ID));
        assertEquals(321L, postIncrementAccount.ethereumNonce());
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

    @Nested
    final class FeeChargingTests {
        // Using non-standard account numbers to tease out any bugs
        private static final long ORIGINAL_PAYER_BALANCE = 100L;
        private static final long ALL_FEES = 12;
        private static final long PAYER_BALANCE_AFTER_ALL_FEES = ORIGINAL_PAYER_BALANCE - ALL_FEES;

        private static final AccountID NODE_ACCOUNT_ID =
                AccountID.newBuilder().accountNum(666).build();
        private static final AccountID FUNDING_ACCOUNT_ID =
                AccountID.newBuilder().accountNum(12).build();
        private static final AccountID STAKING_REWARD_ACCOUNT_ID =
                AccountID.newBuilder().accountNum(13).build();
        private static final AccountID NODE_REWARD_ACCOUNT_ID =
                AccountID.newBuilder().accountNum(14).build();

        private Fees fees;
        private TestConfigBuilder configBuilder;
        private FakeFeeRecordBuilder rb;

        @BeforeEach
        void setUp() {
            configBuilder = HederaTestConfigBuilder.create()
                    .withValue("staking.isEnabled", true)
                    .withValue("staking.fees.nodeRewardPercentage", 10)
                    .withValue("staking.fees.stakingRewardPercentage", 20)
                    .withValue("hedera.shard", 0)
                    .withValue("hedera.realm", 0)
                    .withValue("ledger.fundingAccount", 12)
                    .withValue("accounts.stakingRewardAccount", 13)
                    .withValue("accounts.nodeRewardAccount", 14);

            accountStore.put(Account.newBuilder().accountId(FUNDING_ACCOUNT_ID).build());

            accountStore.put(
                    Account.newBuilder().accountId(STAKING_REWARD_ACCOUNT_ID).build());

            accountStore.put(
                    Account.newBuilder().accountId(NODE_REWARD_ACCOUNT_ID).build());

            accountStore.put(Account.newBuilder().accountId(NODE_ACCOUNT_ID).build());

            accountStore.put(Account.newBuilder()
                    .accountId(EOA_ACCOUNT_ID)
                    .tinybarBalance(ORIGINAL_PAYER_BALANCE)
                    .build());

            rb = new FakeFeeRecordBuilder();
            fees = new Fees(2, 5, 5); // 10 tinybars
        }

        @Test
        void withStakingRewards() {
            // Given that staking is enabled
            final var config =
                    configBuilder.withValue("staking.isEnabled", true).getOrCreateConfig();

            subject = new TokenServiceApiImpl(config, stakingValidator, writableStates);

            // When we charge network+service fees of 10 tinybars and a node fee of 2 tinybars
            subject.chargeFees(EOA_ACCOUNT_ID, NODE_ACCOUNT_ID, fees, rb);

            // Then we find that 10% go to node rewards, 20% to staking rewards, and the rest to the funding account
            final var payerAccount = requireNonNull(accountState.get(EOA_ACCOUNT_ID));
            assertThat(payerAccount.tinybarBalance()).isEqualTo(PAYER_BALANCE_AFTER_ALL_FEES);

            final var nodeRewardAccount = requireNonNull(accountState.get(NODE_REWARD_ACCOUNT_ID));
            assertThat(nodeRewardAccount.tinybarBalance()).isEqualTo(1);

            final var stakingRewardAccount = requireNonNull(accountState.get(STAKING_REWARD_ACCOUNT_ID));
            assertThat(stakingRewardAccount.tinybarBalance()).isEqualTo(2);

            final var fundingAccount = requireNonNull(accountState.get(FUNDING_ACCOUNT_ID));
            assertThat(fundingAccount.tinybarBalance()).isEqualTo(7);

            final var nodeAccount = requireNonNull(accountState.get(NODE_ACCOUNT_ID));
            assertThat(nodeAccount.tinybarBalance()).isEqualTo(2);

            assertThat(rb.transactionFee()).isEqualTo(ALL_FEES);
        }

        @Test
        void withoutStakingRewards() {
            // Given that staking is disabled
            final var config =
                    configBuilder.withValue("staking.isEnabled", false).getOrCreateConfig();

            subject = new TokenServiceApiImpl(config, stakingValidator, writableStates);

            // When we charge fees of 10 tinybars
            subject.chargeFees(EOA_ACCOUNT_ID, NODE_ACCOUNT_ID, fees, rb);

            // Then we find that all the fees go to the funding account
            final var payerAccount = requireNonNull(accountState.get(EOA_ACCOUNT_ID));
            assertThat(payerAccount.tinybarBalance()).isEqualTo(PAYER_BALANCE_AFTER_ALL_FEES);

            final var nodeRewardAccount = requireNonNull(accountState.get(NODE_REWARD_ACCOUNT_ID));
            assertThat(nodeRewardAccount.tinybarBalance()).isZero();

            final var stakingRewardAccount = requireNonNull(accountState.get(STAKING_REWARD_ACCOUNT_ID));
            assertThat(stakingRewardAccount.tinybarBalance()).isZero();

            final var fundingAccount = requireNonNull(accountState.get(FUNDING_ACCOUNT_ID));
            assertThat(fundingAccount.tinybarBalance()).isEqualTo(10);

            assertThat(rb.transactionFee()).isEqualTo(ALL_FEES);
        }

        @Test
        void missingPayerAccount() {
            // When we try to charge a payer account that DOES NOT EXIST, then we get an IllegalStateException.
            final var unknownAccountId =
                    AccountID.newBuilder().accountNum(12345678L).build();
            assertThatThrownBy(() -> subject.chargeFees(unknownAccountId, NODE_ACCOUNT_ID, fees, rb))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Payer account does not exist");
        }

        @Test
        void missingFundingAccount() {
            // Given a configuration that refers to a funding account that DOES NOT EXIST
            final var config =
                    configBuilder.withValue("ledger.fundingAccount", 12345678L).getOrCreateConfig();

            subject = new TokenServiceApiImpl(config, stakingValidator, writableStates);

            // When we try to charge a payer account that DOES exist, then we get an IllegalStateException
            assertThatThrownBy(() -> subject.chargeFees(EOA_ACCOUNT_ID, NODE_ACCOUNT_ID, fees, rb))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Funding account does not exist");
        }

        @Test
        void missingStakingRewardAccount() {
            // Given a configuration that refers to a staking reward account that DOES NOT EXIST
            final var config = configBuilder
                    .withValue("accounts.stakingRewardAccount", 12345678L)
                    .getOrCreateConfig();

            subject = new TokenServiceApiImpl(config, stakingValidator, writableStates);

            // When we try to charge a payer account that DOES exist, then we get an IllegalStateException
            assertThatThrownBy(() -> subject.chargeFees(EOA_ACCOUNT_ID, NODE_ACCOUNT_ID, fees, rb))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Staking reward account does not exist");
        }

        @Test
        void missingNodeRewardAccount() {
            // Given a configuration that refers to a node reward account that DOES NOT EXIST
            final var config = configBuilder
                    .withValue("accounts.nodeRewardAccount", 12345678L)
                    .getOrCreateConfig();

            subject = new TokenServiceApiImpl(config, stakingValidator, writableStates);

            // When we try to charge a payer account that DOES exist, then we get an IllegalStateException
            assertThatThrownBy(() -> subject.chargeFees(EOA_ACCOUNT_ID, NODE_ACCOUNT_ID, fees, rb))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Node reward account does not exist");
        }

        @Test
        void chargesRemainingBalanceIfInsufficient() {
            // Given a payer and unpayable fees, just charge the remaining payer balance
            fees = new Fees(1000, 100, 0); // more than the 100 the user has

            subject = new TokenServiceApiImpl(configBuilder.getOrCreateConfig(), stakingValidator, writableStates);
            subject.chargeFees(EOA_ACCOUNT_ID, NODE_ACCOUNT_ID, fees, rb);

            final var payerAccount = requireNonNull(accountState.get(EOA_ACCOUNT_ID));
            assertThat(payerAccount.tinybarBalance()).isEqualTo(0);
            assertThat(rb.transactionFee()).isEqualTo(100);
        }

        @Test
        void throwsIfUnableToPayNetworkFee() {
            // Given a payer and unpayable fees, just charge the remaining payer balance
            fees = new Fees(0, 101, 0); // more than the 100 the user has

            assertThrows(
                    IllegalArgumentException.class,
                    () -> subject.chargeFees(EOA_ACCOUNT_ID, NODE_ACCOUNT_ID, fees, rb));
        }

        @Test
        void throwsWithNonZeroServiceFeeIfUnableToPayEverything() {
            // Given a payer and unpayable fees, just charge the remaining payer balance
            fees = new Fees(0, 99, 2); // more than the 100 the user has

            assertThrows(
                    IllegalArgumentException.class,
                    () -> subject.chargeFees(EOA_ACCOUNT_ID, NODE_ACCOUNT_ID, fees, rb));
        }
    }
}
