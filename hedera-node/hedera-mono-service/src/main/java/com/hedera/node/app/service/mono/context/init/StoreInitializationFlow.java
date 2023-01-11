/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.context.init;

import com.hedera.node.app.service.mono.context.MutableStateChildren;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.ledger.backing.BackingStore;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.state.migration.HederaTokenRel;
import com.hedera.node.app.service.mono.state.migration.UniqueTokenAdapter;
import com.hedera.node.app.service.mono.state.validation.UsageLimits;
import com.hedera.node.app.service.mono.store.models.NftId;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class StoreInitializationFlow {
    private static final Logger log = LogManager.getLogger(StoreInitializationFlow.class);

    private final UsageLimits usageLimits;
    private final AliasManager aliasManager;
    private final MutableStateChildren workingState;
    private final BackingStore<AccountID, HederaAccount> backingAccounts;
    private final BackingStore<TokenID, MerkleToken> backingTokens;
    private final BackingStore<NftId, UniqueTokenAdapter> backingNfts;
    private final BackingStore<Pair<AccountID, TokenID>, HederaTokenRel> backingTokenRels;

    @Inject
    public StoreInitializationFlow(
            final UsageLimits usageLimits,
            final AliasManager aliasManager,
            final MutableStateChildren workingState,
            final BackingStore<AccountID, HederaAccount> backingAccounts,
            final BackingStore<TokenID, MerkleToken> backingTokens,
            final BackingStore<NftId, UniqueTokenAdapter> backingNfts,
            final BackingStore<Pair<AccountID, TokenID>, HederaTokenRel> backingTokenRels) {
        this.usageLimits = usageLimits;
        this.backingAccounts = backingAccounts;
        this.backingTokens = backingTokens;
        this.workingState = workingState;
        this.backingNfts = backingNfts;
        this.backingTokenRels = backingTokenRels;
        this.aliasManager = aliasManager;
    }

    public void run() {
        backingTokenRels.rebuildFromSources();
        backingAccounts.rebuildFromSources();
        backingTokens.rebuildFromSources();
        backingNfts.rebuildFromSources();
        log.info("Backing stores rebuilt");

        usageLimits.resetNumContracts();
        aliasManager.rebuildAliasesMap(
                workingState.accounts(),
                (num, account) -> {
                    if (account.isSmartContract()) {
                        usageLimits.recordContracts(1);
                    }
                });
        log.info("Account aliases map rebuilt");
    }
}
