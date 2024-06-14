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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
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
import com.hedera.node.app.service.token.impl.schemas.SyntheticAccountCreator;
import com.hedera.node.app.service.token.records.GenesisAccountRecordBuilder;
import com.hedera.node.app.service.token.records.TokenContext;
import java.time.Instant;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Consumer;
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
    private SyntheticAccountCreator syntheticAccountCreator;

    @Mock
    private GenesisAccountRecordBuilder genesisAccountRecordBuilder;

    private GenesisRecordsConsensusHook subject;

    @BeforeEach
    void setup() {
        given(context.readableStore(ReadableBlockRecordStore.class)).willReturn(blockStore);
        given(context.consensusTime()).willReturn(CONSENSUS_NOW);
        given(context.addUncheckedPrecedingChildRecordBuilder(GenesisAccountRecordBuilder.class))
                .willReturn(genesisAccountRecordBuilder);
        given(context.readableStore(ReadableBlockRecordStore.class)).willReturn(blockStore);

        given(blockStore.getLastBlockInfo()).willReturn(defaultStartupBlockInfo());

        subject = new GenesisRecordsConsensusHook(syntheticAccountCreator);
    }

    @Test
    @SuppressWarnings("unchecked")
    void processCreatesAllRecords() {
        final var acctId3 = ACCOUNT_ID_1.copyBuilder().accountNum(3).build();
        final var acct3 = ACCOUNT_1.copyBuilder().accountId(acctId3).build();
        final var acctId4 = ACCOUNT_ID_1.copyBuilder().accountNum(4).build();
        final var acct4 = ACCOUNT_1.copyBuilder().accountId(acctId4).build();
        final var acctId5 = ACCOUNT_ID_1.copyBuilder().accountNum(5).build();
        final var acct5 = ACCOUNT_1.copyBuilder().accountId(acctId5).build();
        final var sysAccts = new TreeSet<>(TokenComparators.ACCOUNT_COMPARATOR);
        sysAccts.add(ACCOUNT_1);
        final var stakingAccts = new TreeSet<>(TokenComparators.ACCOUNT_COMPARATOR);
        stakingAccts.add(ACCOUNT_2);
        final var miscAccts = new TreeSet<>(TokenComparators.ACCOUNT_COMPARATOR);
        miscAccts.add(acct3);
        final var treasuryAccts = new TreeSet<>(TokenComparators.ACCOUNT_COMPARATOR);
        treasuryAccts.add(acct4);
        final var blocklistAccts = new TreeSet<>(TokenComparators.ACCOUNT_COMPARATOR);
        blocklistAccts.add(acct5);
        doAnswer(invocationOnMock -> {
                    ((Consumer<SortedSet<Account>>) invocationOnMock.getArgument(1)).accept(sysAccts);
                    ((Consumer<SortedSet<Account>>) invocationOnMock.getArgument(2)).accept(stakingAccts);
                    ((Consumer<SortedSet<Account>>) invocationOnMock.getArgument(3)).accept(treasuryAccts);
                    ((Consumer<SortedSet<Account>>) invocationOnMock.getArgument(4)).accept(miscAccts);
                    ((Consumer<SortedSet<Account>>) invocationOnMock.getArgument(5)).accept(blocklistAccts);
                    return null;
                })
                .when(syntheticAccountCreator)
                .generateSyntheticAccounts(any(), any(), any(), any(), any(), any());

        // Call the first time to make sure records are generated
        subject.process(context);

        verifyBuilderInvoked(ACCOUNT_ID_1, EXPECTED_SYSTEM_ACCOUNT_CREATION_MEMO, ACCT_1_BALANCE);
        verifyBuilderInvoked(ACCOUNT_ID_2, EXPECTED_STAKING_MEMO);
        verifyBuilderInvoked(acctId3, null);
        verifyBuilderInvoked(acctId4, EXPECTED_TREASURY_CLONE_MEMO);
        verifyBuilderInvoked(acctId5, null);

        // Call process() a second time to make sure no other records are created
        Mockito.clearInvocations(genesisAccountRecordBuilder);
        assertThatThrownBy(() -> subject.process(context)).isInstanceOf(NullPointerException.class);
        verifyNoInteractions(genesisAccountRecordBuilder);
    }

    @Test
    void processCreatesNoRecordsWhenEmpty() {
        subject.process(context);
        verifyNoInteractions(genesisAccountRecordBuilder);
        verify(context, never()).markMigrationRecordsStreamed();
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
