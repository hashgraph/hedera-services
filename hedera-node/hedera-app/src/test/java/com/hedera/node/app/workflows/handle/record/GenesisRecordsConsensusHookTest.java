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

package com.hedera.node.app.workflows.handle.record;

import static com.hedera.node.app.spi.HapiUtils.FUNDING_ACCOUNT_EXPIRY;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.token.CryptoCreateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.records.ReadableBlockRecordStore;
import com.hedera.node.app.service.token.records.GenesisAccountRecordBuilder;
import com.hedera.node.app.service.token.records.TokenContext;
import java.time.Instant;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GenesisRecordsConsensusHookTest {
    private static final AccountID ACCOUNT_ID_1 =
            AccountID.newBuilder().accountNum(1).build();
    private static final AccountID ACCOUNT_ID_2 =
            AccountID.newBuilder().accountNum(2).build();
    private static final Account ACCOUNT_1 =
            Account.newBuilder().accountId(ACCOUNT_ID_1).build();
    private static final Account ACCOUNT_2 =
            Account.newBuilder().accountId(ACCOUNT_ID_2).build();
    private static final CryptoCreateTransactionBody ACCT_1_CREATE =
            CryptoCreateTransactionBody.newBuilder().memo("builder1").build();
    private static final CryptoCreateTransactionBody ACCT_2_CREATE =
            CryptoCreateTransactionBody.newBuilder().memo("builder2").build();
    private static final Instant CONSENSUS_NOW = Instant.parse("2023-08-10T00:00:00Z");

    private static final String EXPECTED_SYSTEM_ACCOUNT_CREATION_MEMO = "Synthetic system creation";
    private static final String EXPECTED_STAKING_MEMO = "Release 0.24.1 migration record";
    private static final String EXPECTED_TREASURY_CLONE_MEMO = "Synthetic zero-balance treasury clone";

    @Mock(strictness = Mock.Strictness.LENIENT)
    private TokenContext context;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private ReadableBlockRecordStore blockStore;

    @Mock
    private GenesisAccountRecordBuilder genesisAccountRecordBuilder;

    private GenesisRecordsConsensusHook subject;

    @BeforeEach
    void setUp() {
        given(context.readableStore(ReadableBlockRecordStore.class)).willReturn(blockStore);
        given(context.consensusTime()).willReturn(CONSENSUS_NOW);
        given(context.addUncheckedPrecedingChildRecordBuilder(GenesisAccountRecordBuilder.class))
                .willReturn(genesisAccountRecordBuilder);

        given(blockStore.getLastBlockInfo()).willReturn(defaultStartupBlockInfo());

        subject = new GenesisRecordsConsensusHook();
    }

    @Test
    void processCreatesSystemAccounts() {
        subject.systemAccounts(Map.of(ACCOUNT_1, ACCT_1_CREATE.copyBuilder(), ACCOUNT_2, ACCT_2_CREATE.copyBuilder()));
        subject.process(context);

        verifyBuilderInvoked(ACCOUNT_ID_1, ACCT_1_CREATE, EXPECTED_SYSTEM_ACCOUNT_CREATION_MEMO);
        verifyBuilderInvoked(ACCOUNT_ID_2, ACCT_2_CREATE, EXPECTED_SYSTEM_ACCOUNT_CREATION_MEMO);
    }

    @Test
    void processCreatesStakingAccountsWithImplicitExpiry() {
        subject.stakingAccounts(Map.of(ACCOUNT_1, ACCT_1_CREATE.copyBuilder(), ACCOUNT_2, ACCT_2_CREATE.copyBuilder()));

        subject.process(context);

        final var expectedAutoRenew = FUNDING_ACCOUNT_EXPIRY - CONSENSUS_NOW.getEpochSecond();
        verifyBuilderInvoked(
                ACCOUNT_ID_1,
                ACCT_1_CREATE
                        .copyBuilder()
                        .autoRenewPeriod(
                                Duration.newBuilder().seconds(expectedAutoRenew).build())
                        .build(),
                EXPECTED_STAKING_MEMO);
        verifyBuilderInvoked(
                ACCOUNT_ID_2,
                ACCT_2_CREATE
                        .copyBuilder()
                        .autoRenewPeriod(
                                Duration.newBuilder().seconds(expectedAutoRenew).build())
                        .build(),
                EXPECTED_STAKING_MEMO);
    }

    @Test
    void processCreatesMultipurposeAccounts() {
        subject.miscAccounts(Map.of(ACCOUNT_1, ACCT_1_CREATE.copyBuilder(), ACCOUNT_2, ACCT_2_CREATE.copyBuilder()));

        subject.process(context);

        verifyBuilderInvoked(ACCOUNT_ID_1, ACCT_1_CREATE, null);
        verifyBuilderInvoked(ACCOUNT_ID_2, ACCT_2_CREATE, null);
    }

    @Test
    void processCreatesTreasuryClones() {
        subject.treasuryClones(Map.of(ACCOUNT_1, ACCT_1_CREATE.copyBuilder(), ACCOUNT_2, ACCT_2_CREATE.copyBuilder()));

        subject.process(context);

        verifyBuilderInvoked(ACCOUNT_ID_1, ACCT_1_CREATE, EXPECTED_TREASURY_CLONE_MEMO);
        verifyBuilderInvoked(ACCOUNT_ID_2, ACCT_2_CREATE, EXPECTED_TREASURY_CLONE_MEMO);
    }

    @Test
    void processCreatesBlocklistAccounts() {
        subject.blocklistAccounts(
                Map.of(ACCOUNT_1, ACCT_1_CREATE.copyBuilder(), ACCOUNT_2, ACCT_2_CREATE.copyBuilder()));

        subject.process(context);

        verifyBuilderInvoked(ACCOUNT_ID_1, ACCT_1_CREATE, null);
        verifyBuilderInvoked(ACCOUNT_ID_2, ACCT_2_CREATE, null);
    }

    @Test
    void processCreatesAllRecords() {
        final var acctId3 = ACCOUNT_ID_1.copyBuilder().accountNum(3).build();
        final var acct3 = ACCOUNT_1.copyBuilder().accountId(acctId3).build();
        final var acct3Create = ACCT_1_CREATE.copyBuilder().memo("builder3").build();
        final var acctId4 = ACCOUNT_ID_1.copyBuilder().accountNum(4).build();
        final var acct4 = ACCOUNT_1.copyBuilder().accountId(acctId4).build();
        final var acct4Create = ACCT_1_CREATE.copyBuilder().memo("builder4").build();
        final var acctId5 = ACCOUNT_ID_1.copyBuilder().accountNum(5).build();
        final var acct5 = ACCOUNT_1.copyBuilder().accountId(acctId5).build();
        final var acct5Create = ACCT_1_CREATE.copyBuilder().memo("builder5").build();
        subject.systemAccounts(Map.of(ACCOUNT_1, ACCT_1_CREATE.copyBuilder()));
        subject.stakingAccounts(Map.of(ACCOUNT_2, ACCT_2_CREATE.copyBuilder()));
        subject.miscAccounts(Map.of(acct3, acct3Create.copyBuilder()));
        subject.treasuryClones(Map.of(acct4, acct4Create.copyBuilder()));
        subject.blocklistAccounts(Map.of(acct5, acct5Create.copyBuilder()));

        // Call the first time to make sure records are generated
        subject.process(context);

        verifyBuilderInvoked(ACCOUNT_ID_1, ACCT_1_CREATE, EXPECTED_SYSTEM_ACCOUNT_CREATION_MEMO);
        verifyBuilderInvoked(
                ACCOUNT_ID_2,
                ACCT_2_CREATE
                        .copyBuilder()
                        .autoRenewPeriod(Duration.newBuilder()
                                .seconds(FUNDING_ACCOUNT_EXPIRY - CONSENSUS_NOW.getEpochSecond())
                                .build())
                        .build(),
                EXPECTED_STAKING_MEMO);
        verifyBuilderInvoked(acctId3, acct3Create, null);
        verifyBuilderInvoked(acctId4, acct4Create, EXPECTED_TREASURY_CLONE_MEMO);
        verifyBuilderInvoked(acctId5, acct5Create, null);

        // Call process() a second time to make sure no other records are created
        Mockito.clearInvocations(genesisAccountRecordBuilder);
        subject.process(context);
        verifyNoInteractions(genesisAccountRecordBuilder);
    }

    @Test
    void processCreatesNoRecordsWhenEmpty() {
        subject.process(context);
        verifyNoInteractions(genesisAccountRecordBuilder);
    }

    @Test
    void processCreatesNoRecordsAfterRunning() {
        given(blockStore.getLastBlockInfo())
                .willReturn(defaultStartupBlockInfo()
                        .copyBuilder()
                        .consTimeOfLastHandledTxn(Timestamp.newBuilder()
                                .seconds(CONSENSUS_NOW.getEpochSecond())
                                .nanos(CONSENSUS_NOW.getNano()))
                        .build());
        // Add a single account, so we know the subject isn't skipping processing because there's no data
        subject.stakingAccounts(
                Map.of(Account.newBuilder().accountId(ACCOUNT_ID_1).build(), ACCT_1_CREATE.copyBuilder()));

        subject.process(context);

        verifyNoInteractions(genesisAccountRecordBuilder);
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void systemAccountsNullParam() {
        Assertions.assertThatThrownBy(() -> subject.systemAccounts(null)).isInstanceOf(NullPointerException.class);
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void stakingAccountsNullParam() {
        Assertions.assertThatThrownBy(() -> subject.stakingAccounts(null)).isInstanceOf(NullPointerException.class);
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void multipurposeAccountsNullParam() {
        Assertions.assertThatThrownBy(() -> subject.miscAccounts(null)).isInstanceOf(NullPointerException.class);
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void treasuryAccountsNullParam() {
        Assertions.assertThatThrownBy(() -> subject.treasuryClones(null)).isInstanceOf(NullPointerException.class);
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void blocklistAccountsNullParam() {
        Assertions.assertThatThrownBy(() -> subject.blocklistAccounts(null)).isInstanceOf(NullPointerException.class);
    }

    private void verifyBuilderInvoked(
            final AccountID acctId, final CryptoCreateTransactionBody acctCreateBody, final String expectedMemo) {
        verify(genesisAccountRecordBuilder).accountID(acctId);
        verify(genesisAccountRecordBuilder).transaction(asCryptoCreateTxn(acctCreateBody));
        if (expectedMemo != null)
            verify(genesisAccountRecordBuilder, atLeastOnce()).memo(expectedMemo);
        //noinspection DataFlowIssue
        verify(genesisAccountRecordBuilder, Mockito.never()).memo(null);
    }

    private static Transaction asCryptoCreateTxn(CryptoCreateTransactionBody body) {
        return Transaction.newBuilder()
                .body(TransactionBody.newBuilder().cryptoCreateAccount(body))
                .build();
    }

    private static BlockInfo defaultStartupBlockInfo() {
        return BlockInfo.newBuilder()
                .consTimeOfLastHandledTxn((Timestamp) null)
                .migrationRecordsStreamed(false)
                .build();
    }
}
