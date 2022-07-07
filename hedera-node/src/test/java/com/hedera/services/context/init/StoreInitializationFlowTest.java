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

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.services.context.MutableStateChildren;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.ledger.backing.BackingStore;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.merkle.map.MerkleMap;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StoreInitializationFlowTest {
    @Mock private TokenStore tokenStore;
    @Mock private ScheduleStore scheduleStore;
    @Mock private MutableStateChildren workingState;
    @Mock private AliasManager aliasManager;
    @Mock private BackingStore<AccountID, MerkleAccount> backingAccounts;
    @Mock private BackingStore<NftId, MerkleUniqueToken> backingNfts;
    @Mock private BackingStore<TokenID, MerkleToken> backingTokens;
    @Mock private BackingStore<Pair<AccountID, TokenID>, MerkleTokenRelStatus> backingTokenRels;
    @Mock private MerkleMap<EntityNum, MerkleAccount> accounts;

    private StoreInitializationFlow subject;

    @BeforeEach
    void setUp() {
        subject =
                new StoreInitializationFlow(
                        tokenStore,
                        scheduleStore,
                        aliasManager,
                        workingState,
                        backingAccounts,
                        backingTokens,
                        backingNfts,
                        backingTokenRels);
    }

    @Test
    void initsAsExpected() {
        given(workingState.accounts()).willReturn(accounts);

        // when:
        subject.run();

        // then:
        verify(backingTokenRels).rebuildFromSources();
        verify(backingAccounts).rebuildFromSources();
        verify(backingNfts).rebuildFromSources();
        verify(tokenStore).rebuildViews();
        verify(scheduleStore).rebuildViews();
        verify(aliasManager).rebuildAliasesMap(accounts);
    }
}
