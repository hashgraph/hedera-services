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

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.verifyNoInteractions;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.token.CryptoCreateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.records.GenesisAccountRecordBuilder;
import com.hedera.node.app.spi.workflows.HandleContext;
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
class GenesisRecordsConsensusHookImplTest {
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

    @Mock(strictness = Mock.Strictness.LENIENT)
    private HandleContext context;

    @Mock
    private GenesisAccountRecordBuilder genesisAccountRecordBuilder;

    private GenesisRecordsConsensusHookImpl subject;

    @BeforeEach
    void setup() {
        given(context.addPrecedingChildRecordBuilder(GenesisAccountRecordBuilder.class))
                .willReturn(genesisAccountRecordBuilder);

        subject = new GenesisRecordsConsensusHookImpl();
    }

    @Test
    void processCreatesSystemAccounts() {
        subject.systemAccounts(Map.of(ACCOUNT_1, ACCT_1_CREATE.copyBuilder(), ACCOUNT_2, ACCT_2_CREATE.copyBuilder()));

        subject.process(CONSENSUS_NOW, context);

        verifyBuilderInvoked(ACCOUNT_ID_1, ACCT_1_CREATE);
        verifyBuilderInvoked(ACCOUNT_ID_2, ACCT_2_CREATE);
    }

    @Test
    void processCreatesStakingAccounts() {
        subject.stakingAccounts(Map.of(ACCOUNT_1, ACCT_1_CREATE.copyBuilder(), ACCOUNT_2, ACCT_2_CREATE.copyBuilder()));

        subject.process(CONSENSUS_NOW, context);

        verifyBuilderInvoked(ACCOUNT_ID_1, ACCT_1_CREATE);
        verifyBuilderInvoked(ACCOUNT_ID_2, ACCT_2_CREATE);
    }

    @Test
    void processCreatesMultipurposeAccounts() {
        subject.multipurposeAccounts(
                Map.of(ACCOUNT_1, ACCT_1_CREATE.copyBuilder(), ACCOUNT_2, ACCT_2_CREATE.copyBuilder()));

        subject.process(CONSENSUS_NOW, context);

        verifyBuilderInvoked(ACCOUNT_ID_1, ACCT_1_CREATE);
        verifyBuilderInvoked(ACCOUNT_ID_2, ACCT_2_CREATE);
    }

    @Test
    void processCreatesTreasuryClones() {
        subject.treasuryClones(Map.of(ACCOUNT_1, ACCT_1_CREATE.copyBuilder(), ACCOUNT_2, ACCT_2_CREATE.copyBuilder()));

        subject.process(CONSENSUS_NOW, context);

        verifyBuilderInvoked(ACCOUNT_ID_1, ACCT_1_CREATE);
        verifyBuilderInvoked(ACCOUNT_ID_2, ACCT_2_CREATE);
    }

    @Test
    void processCreatesAllRecords() {
        final var acctId3 = ACCOUNT_ID_1.copyBuilder().accountNum(3).build();
        final var acct3 = ACCOUNT_1.copyBuilder().accountId(acctId3).build();
        final var acct3Create = ACCT_1_CREATE.copyBuilder().memo("builder3").build();
        final var acctId4 = ACCOUNT_ID_1.copyBuilder().accountNum(4).build();
        final var acct4 = ACCOUNT_1.copyBuilder().accountId(acctId4).build();
        final var acct4Create = ACCT_1_CREATE.copyBuilder().memo("builder4").build();
        subject.systemAccounts(Map.of(ACCOUNT_1, ACCT_1_CREATE.copyBuilder()));
        subject.stakingAccounts(Map.of(ACCOUNT_2, ACCT_2_CREATE.copyBuilder()));
        subject.multipurposeAccounts(Map.of(acct3, acct3Create.copyBuilder()));
        subject.treasuryClones(Map.of(acct4, acct4Create.copyBuilder()));

        // Call the first time to make sure records are generated
        subject.process(CONSENSUS_NOW, context);

        verifyBuilderInvoked(ACCOUNT_ID_1, ACCT_1_CREATE);
        verifyBuilderInvoked(ACCOUNT_ID_2, ACCT_2_CREATE);
        verifyBuilderInvoked(acctId3, acct3Create);
        verifyBuilderInvoked(acctId4, acct4Create);

        // Call process() a second time to make sure no other records are created
        Mockito.clearInvocations(genesisAccountRecordBuilder);
        subject.process(CONSENSUS_NOW, context);
        verifyNoInteractions(genesisAccountRecordBuilder);
    }

    @Test
    void processCreatesNoRecordsWhenEmpty() {
        subject.process(CONSENSUS_NOW, context);
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
        Assertions.assertThatThrownBy(() -> subject.multipurposeAccounts(null))
                .isInstanceOf(NullPointerException.class);
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void treasuryAccountsNullParam() {
        Assertions.assertThatThrownBy(() -> subject.treasuryClones(null)).isInstanceOf(NullPointerException.class);
    }

    private void verifyBuilderInvoked(final AccountID acctId, final CryptoCreateTransactionBody acctCreateBody) {
        verify(genesisAccountRecordBuilder).accountID(acctId);
        verify(genesisAccountRecordBuilder).transaction(asCryptoCreateTxn(acctCreateBody));
    }

    private static Transaction asCryptoCreateTxn(CryptoCreateTransactionBody body) {
        return Transaction.newBuilder()
                .body(TransactionBody.newBuilder().cryptoCreateAccount(body))
                .build();
    }
}
