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
package com.hedera.node.app.service.mono.ledger;

import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.ledger.backing.BackingAccounts;
import com.hedera.node.app.service.mono.ledger.backing.BackingStore;
import com.hedera.node.app.service.mono.ledger.ids.EntityIdSource;
import com.hedera.node.app.service.mono.ledger.properties.AccountProperty;
import com.hedera.node.app.service.mono.ledger.properties.TokenProperty;
import com.hedera.node.app.service.mono.records.RecordsHistorian;
import com.hedera.node.app.service.mono.state.EntityCreator;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.store.schedule.ScheduleStore;
import com.hedera.node.app.service.mono.store.tokens.TokenStore;
import com.hedera.node.app.service.mono.txns.crypto.AutoCreationLogic;
import com.hedera.node.app.service.mono.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;

@Module
public interface LedgerModule {
    @Binds
    @Singleton
    BackingStore<AccountID, HederaAccount> bindBackingAccounts(BackingAccounts backingAccounts);

    @Provides
    @Singleton
    static HederaLedger provideHederaLedger(
            final TokenStore tokenStore,
            final ScheduleStore scheduleStore,
            final EntityCreator creator,
            final EntityIdSource ids,
            final OptionValidator validator,
            final SideEffectsTracker sideEffectsTracker,
            final RecordsHistorian recordsHistorian,
            final TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokensLedger,
            final TransactionalLedger<AccountID, AccountProperty, HederaAccount> accountsLedger,
            final AutoCreationLogic autoCreationLogic,
            final TransferLogic transferLogic) {
        final var ledger =
                new HederaLedger(
                        tokenStore,
                        ids,
                        creator,
                        validator,
                        sideEffectsTracker,
                        recordsHistorian,
                        tokensLedger,
                        accountsLedger,
                        transferLogic,
                        autoCreationLogic);
        scheduleStore.setAccountsLedger(accountsLedger);
        scheduleStore.setHederaLedger(ledger);
        return ledger;
    }
}
