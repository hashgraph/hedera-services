/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.mock;

import com.hedera.node.app.service.mono.config.MockGlobalDynamicProps;
import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.ledger.accounts.HederaAccountCustomizer;
import com.hedera.node.app.service.mono.ledger.backing.BackingTokenRels;
import com.hedera.node.app.service.mono.ledger.backing.HashMapBackingAccounts;
import com.hedera.node.app.service.mono.ledger.backing.HashMapBackingNfts;
import com.hedera.node.app.service.mono.ledger.backing.HashMapBackingTokenRels;
import com.hedera.node.app.service.mono.ledger.backing.HashMapBackingTokens;
import com.hedera.node.app.service.mono.ledger.interceptors.AccountsCommitInterceptor;
import com.hedera.node.app.service.mono.ledger.interceptors.AutoAssocTokenRelsCommitInterceptor;
import com.hedera.node.app.service.mono.ledger.interceptors.LinkAwareUniqueTokensCommitInterceptor;
import com.hedera.node.app.service.mono.ledger.properties.AccountProperty;
import com.hedera.node.app.service.mono.ledger.properties.ChangeSummaryManager;
import com.hedera.node.app.service.mono.ledger.properties.NftProperty;
import com.hedera.node.app.service.mono.ledger.properties.TokenProperty;
import com.hedera.node.app.service.mono.ledger.properties.TokenRelProperty;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.merkle.MerkleTokenRelStatus;
import com.hedera.node.app.service.mono.state.migration.UniqueTokenAdapter;
import com.hedera.node.app.service.mono.state.validation.UsageLimits;
import com.hedera.node.app.service.mono.store.contracts.MutableEntityAccess;
import com.hedera.node.app.service.mono.store.tokens.HederaTokenStore;
import com.hedera.node.app.service.mono.txns.crypto.AutoCreationLogic;
import com.hedera.test.mocks.TestContextValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HederaLedgerLiveTest extends BaseHederaLedgerTestHelper {

    @Mock private UsageLimits usageLimits;
    @Mock private AutoCreationLogic autoCreationLogic;
    @Mock private AutoAssocTokenRelsCommitInterceptor autoAssocTokenRelsCommitInterceptor;
    @Mock private AccountsCommitInterceptor accountsCommitInterceptor;
    @Mock private LinkAwareUniqueTokensCommitInterceptor linkAwareUniqueTokensCommitInterceptor;

    final SideEffectsTracker liveSideEffects = new SideEffectsTracker();

    @BeforeEach
    void setup() {
        commonSetup();

        accountsLedger =
                new TransactionalLedger<>(
                        AccountProperty.class,
                        MerkleAccount::new,
                        new HashMapBackingAccounts(),
                        new ChangeSummaryManager<>());
        accountsLedger.setCommitInterceptor(accountsCommitInterceptor);

        nftsLedger =
                new TransactionalLedger<>(
                        NftProperty.class,
                        UniqueTokenAdapter::newEmptyMerkleToken,
                        new HashMapBackingNfts(),
                        new ChangeSummaryManager<>());
        nftsLedger.setCommitInterceptor(linkAwareUniqueTokensCommitInterceptor);
        tokenRelsLedger =
                new TransactionalLedger<>(
                        TokenRelProperty.class,
                        MerkleTokenRelStatus::new,
                        new HashMapBackingTokenRels(),
                        new ChangeSummaryManager<>());
        tokenRelsLedger.setKeyToString(BackingTokenRels::readableTokenRel);
        tokenRelsLedger.setCommitInterceptor(autoAssocTokenRelsCommitInterceptor);
        tokensLedger =
                new TransactionalLedger<>(
                        TokenProperty.class,
                        MerkleToken::new,
                        new HashMapBackingTokens(),
                        new ChangeSummaryManager<>());
        tokenStore =
                new HederaTokenStore(
                        ids,
                        usageLimits,
                        TestContextValidator.TEST_VALIDATOR,
                        liveSideEffects,
                        new MockGlobalDynamicProps(),
                        tokenRelsLedger,
                        nftsLedger,
                        new HashMapBackingTokens());
        subject =
                new HederaLedger(
                        tokenStore,
                        ids,
                        creator,
                        validator,
                        liveSideEffects,
                        historian,
                        tokensLedger,
                        accountsLedger,
                        transferLogic,
                        autoCreationLogic);
        subject.setMutableEntityAccess(mock(MutableEntityAccess.class));
    }

    @Test
    void recordsCreationOfAccountDeletedInSameTxn() {
        subject.begin();
        final var a = subject.create(genesis, 1_000L, new HederaAccountCustomizer().memo("a"));
        subject.delete(a, genesis);
        final var numNetTransfersAccounts = subject.netTransfersInTxn().getAccountNums().length;
        final var numNetTransfersBalances = subject.netTransfersInTxn().getHbars().length;
        subject.commit();

        assertEquals(0, numNetTransfersAccounts);
        assertEquals(0, numNetTransfersBalances);
        assertTrue(subject.exists(a));
        assertEquals(GENESIS_BALANCE, subject.getBalance(genesis));
    }

    @Test
    void addsRecordsAndEntitiesBeforeCommitting() {
        subject.begin();
        subject.create(genesis, 1_000L, new HederaAccountCustomizer().memo("a"));
        subject.commit();

        verify(historian).saveExpirableTransactionRecords();
        verify(historian).noteNewExpirationEvents();
    }

    @Test
    void showsInconsistentStateIfSpawnFails() {
        subject.begin();
        subject.create(genesis, 1_000L, new HederaAccountCustomizer().memo("a"));
        subject.commit();

        ids.reclaimLastId();
        liveSideEffects.reset();
        subject.begin();
        final var customizer = new HederaAccountCustomizer().memo("a");
        assertThrows(
                IllegalArgumentException.class, () -> subject.create(genesis, 1_000L, customizer));
    }

    @Test
    void recognizesPendingCreates() {
        subject.begin();
        final var a = subject.create(genesis, 1L, new HederaAccountCustomizer().memo("a"));

        assertTrue(subject.isPendingCreation(a));
        assertFalse(subject.isPendingCreation(genesis));
    }
}
