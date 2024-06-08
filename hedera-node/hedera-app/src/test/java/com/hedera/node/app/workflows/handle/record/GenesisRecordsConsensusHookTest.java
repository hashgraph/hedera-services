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

package com.hedera.node.app.workflows.handle.record;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.records.ReadableBlockRecordStore;
import com.hedera.node.app.service.token.impl.comparator.TokenComparators;
import com.hedera.node.app.service.token.records.GenesisAccountRecordBuilder;
import com.hedera.node.app.service.token.records.TokenContext;
import java.time.Instant;
import java.util.TreeSet;
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
    private static final int ACCT_1_BALANCE = 25;
    private static final Account ACCOUNT_1 = Account.newBuilder()
            .accountId(ACCOUNT_ID_1)
            .tinybarBalance(ACCT_1_BALANCE)
            .build();
    private static final Account ACCOUNT_2 =
            Account.newBuilder().accountId(ACCOUNT_ID_2).build();
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
    void setup() {
        given(context.readableStore(ReadableBlockRecordStore.class)).willReturn(blockStore);
        given(context.consensusTime()).willReturn(CONSENSUS_NOW);
        given(context.addUncheckedPrecedingChildRecordBuilder(GenesisAccountRecordBuilder.class))
                .willReturn(genesisAccountRecordBuilder);
        given(context.isFirstTransaction()).willReturn(true);
        given(context.readableStore(ReadableBlockRecordStore.class)).willReturn(blockStore);

        given(blockStore.getLastBlockInfo()).willReturn(defaultStartupBlockInfo());

        subject = new GenesisRecordsConsensusHook();
    }

    @Test
    void processCreatesSystemAccounts() {
        final var accts = new TreeSet<>(TokenComparators.ACCOUNT_COMPARATOR);
        accts.add(ACCOUNT_1);
        accts.add(ACCOUNT_2);
        subject.systemAccounts(accts);
        subject.process(context);

        verifyBuilderInvoked(ACCOUNT_ID_1, EXPECTED_SYSTEM_ACCOUNT_CREATION_MEMO, ACCT_1_BALANCE);
        verifyBuilderInvoked(ACCOUNT_ID_2, EXPECTED_SYSTEM_ACCOUNT_CREATION_MEMO);
        verify(context).markMigrationRecordsStreamed();
    }

    @Test
    void processCreatesStakingAccounts() {
        final var accts = new TreeSet<>(TokenComparators.ACCOUNT_COMPARATOR);
        accts.add(ACCOUNT_1);
        accts.add(ACCOUNT_2);
        subject.stakingAccounts(accts);

        subject.process(context);

        verifyBuilderInvoked(ACCOUNT_ID_1, EXPECTED_STAKING_MEMO, ACCT_1_BALANCE);
        verifyBuilderInvoked(ACCOUNT_ID_2, EXPECTED_STAKING_MEMO);
        verify(context).markMigrationRecordsStreamed();
    }

    @Test
    void processCreatesMultipurposeAccounts() {
        final var accts = new TreeSet<>(TokenComparators.ACCOUNT_COMPARATOR);
        accts.add(ACCOUNT_1);
        accts.add(ACCOUNT_2);
        subject.miscAccounts(accts);

        subject.process(context);

        verifyBuilderInvoked(ACCOUNT_ID_1, null, ACCT_1_BALANCE);
        verifyBuilderInvoked(ACCOUNT_ID_2, null);
        verify(context).markMigrationRecordsStreamed();
    }

    @Test
    void processCreatesTreasuryClones() {
        final var accts = new TreeSet<>(TokenComparators.ACCOUNT_COMPARATOR);
        accts.add(ACCOUNT_1);
        accts.add(ACCOUNT_2);
        subject.treasuryClones(accts);

        subject.process(context);

        verifyBuilderInvoked(ACCOUNT_ID_1, EXPECTED_TREASURY_CLONE_MEMO, ACCT_1_BALANCE);
        verifyBuilderInvoked(ACCOUNT_ID_2, EXPECTED_TREASURY_CLONE_MEMO);
        verify(context).markMigrationRecordsStreamed();
    }

    @Test
    void processCreatesBlocklistAccounts() {
        final var accts = new TreeSet<>(TokenComparators.ACCOUNT_COMPARATOR);
        accts.add(ACCOUNT_1);
        accts.add(ACCOUNT_2);
        subject.blocklistAccounts(accts);

        subject.process(context);

        verifyBuilderInvoked(ACCOUNT_ID_1, null, ACCT_1_BALANCE);
        verifyBuilderInvoked(ACCOUNT_ID_2, null);
        verify(context).markMigrationRecordsStreamed();
    }

    @Test
    void processCreatesAllRecords() {
        final var acctId3 = ACCOUNT_ID_1.copyBuilder().accountNum(3).build();
        final var acct3 = ACCOUNT_1.copyBuilder().accountId(acctId3).build();
        final var acctId4 = ACCOUNT_ID_1.copyBuilder().accountNum(4).build();
        final var acct4 = ACCOUNT_1.copyBuilder().accountId(acctId4).build();
        final var acctId5 = ACCOUNT_ID_1.copyBuilder().accountNum(5).build();
        final var acct5 = ACCOUNT_1.copyBuilder().accountId(acctId5).build();
        final var sysAccts = new TreeSet<>(TokenComparators.ACCOUNT_COMPARATOR);
        sysAccts.add(ACCOUNT_1);
        subject.systemAccounts(sysAccts);
        final var stakingAccts = new TreeSet<>(TokenComparators.ACCOUNT_COMPARATOR);
        stakingAccts.add(ACCOUNT_2);
        subject.stakingAccounts(stakingAccts);
        final var miscAccts = new TreeSet<>(TokenComparators.ACCOUNT_COMPARATOR);
        miscAccts.add(acct3);
        subject.miscAccounts(miscAccts);
        final var treasuryAccts = new TreeSet<>(TokenComparators.ACCOUNT_COMPARATOR);
        treasuryAccts.add(acct4);
        subject.treasuryClones(treasuryAccts);
        final var blocklistAccts = new TreeSet<>(TokenComparators.ACCOUNT_COMPARATOR);
        blocklistAccts.add(acct5);
        subject.blocklistAccounts(blocklistAccts);

        // Call the first time to make sure records are generated
        subject.process(context);

        verifyBuilderInvoked(ACCOUNT_ID_1, EXPECTED_SYSTEM_ACCOUNT_CREATION_MEMO, ACCT_1_BALANCE);
        verifyBuilderInvoked(ACCOUNT_ID_2, EXPECTED_STAKING_MEMO);
        verifyBuilderInvoked(acctId3, null);
        verifyBuilderInvoked(acctId4, EXPECTED_TREASURY_CLONE_MEMO);
        verifyBuilderInvoked(acctId5, null);
        verify(context).markMigrationRecordsStreamed();

        // Call process() a second time to make sure no other records are created
        Mockito.clearInvocations(genesisAccountRecordBuilder);
        subject.process(context);
        verifyNoInteractions(genesisAccountRecordBuilder);
    }

    @Test
    void processCreatesNoRecordsWhenEmpty() {
        subject.process(context);
        verifyNoInteractions(genesisAccountRecordBuilder);
        verify(context, never()).markMigrationRecordsStreamed();
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

    private void verifyBuilderInvoked(final AccountID acctId, final String expectedMemo) {
        verifyBuilderInvoked(acctId, expectedMemo, 0);
    }

    private void verifyBuilderInvoked(final AccountID acctId, final String expectedMemo, final long expectedBalance) {
        verify(genesisAccountRecordBuilder).accountID(acctId);

        if (expectedMemo != null)
            verify(genesisAccountRecordBuilder, atLeastOnce()).memo(expectedMemo);

        //noinspection DataFlowIssue
        verify(genesisAccountRecordBuilder, Mockito.never()).memo(null);

        if (expectedBalance != 0) {
            verify(genesisAccountRecordBuilder)
                    .transferList(eq(TransferList.newBuilder()
                            .accountAmounts(AccountAmount.newBuilder()
                                    .accountID(acctId)
                                    .amount(expectedBalance)
                                    .build())
                            .build()));
        }
    }

    private static BlockInfo defaultStartupBlockInfo() {
        return BlockInfo.newBuilder()
                .consTimeOfLastHandledTxn((Timestamp) null)
                .migrationRecordsStreamed(false)
                .build();
    }
}
