/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.context.init;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hedera.services.context.MutableStateChildren;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.ledger.backing.BackingStore;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.migration.AccountStorageAdapter;
import com.hedera.services.state.migration.HederaAccount;
import com.hedera.services.state.migration.UniqueTokenAdapter;
import com.hedera.services.state.validation.UsageLimits;
import com.hedera.services.store.models.NftId;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.merkle.map.MerkleMap;
import java.util.function.BiConsumer;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StoreInitializationFlowTest {
    @Mock private MutableStateChildren workingState;

    @Mock private UsageLimits usageLimits;
    @Mock private AliasManager aliasManager;
    @Mock private BackingStore<AccountID, HederaAccount> backingAccounts;
    @Mock private BackingStore<NftId, UniqueTokenAdapter> backingNfts;
    @Mock private BackingStore<TokenID, MerkleToken> backingTokens;
    @Mock private BackingStore<Pair<AccountID, TokenID>, MerkleTokenRelStatus> backingTokenRels;
    @Mock private MerkleMap<EntityNum, MerkleAccount> accounts;

    private StoreInitializationFlow subject;

    @BeforeEach
    void setUp() {
        subject =
                new StoreInitializationFlow(
                        usageLimits,
                        aliasManager,
                        workingState,
                        backingAccounts,
                        backingTokens,
                        backingNfts,
                        backingTokenRels);
    }

    @Test
    @SuppressWarnings("unchecked")
    void initsAsExpected() {
        final ArgumentCaptor<BiConsumer<EntityNum, HederaAccount>> captor =
                ArgumentCaptor.forClass(BiConsumer.class);
        given(workingState.accounts()).willReturn(AccountStorageAdapter.fromInMemory(accounts));

        // when:
        subject.run();

        // then:
        verify(backingTokenRels).rebuildFromSources();
        verify(backingAccounts).rebuildFromSources();
        verify(backingNfts).rebuildFromSources();
        verify(usageLimits).resetNumContracts();
        verify(aliasManager).rebuildAliasesMap(any(), captor.capture());
        final var observer = captor.getValue();
        observer.accept(EntityNum.fromInt(1), MerkleAccountFactory.newAccount().get());
        observer.accept(EntityNum.fromInt(2), MerkleAccountFactory.newContract().get());
        observer.accept(EntityNum.fromInt(3), MerkleAccountFactory.newContract().get());
        verify(usageLimits, times(2)).recordContracts(1);
    }
}
