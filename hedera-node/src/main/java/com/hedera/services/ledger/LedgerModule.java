package com.hedera.services.ledger;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.accounts.BackingAccounts;
import com.hedera.services.ledger.accounts.BackingStore;
import com.hedera.services.ledger.accounts.BackingTokens;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.ChangeSummaryManager;
import com.hedera.services.ledger.properties.TokenProperty;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.store.tokens.views.UniqTokenViewsManager;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;

@Module
public abstract class LedgerModule {
	@Binds
	@Singleton
	public abstract BackingStore<AccountID, MerkleAccount> bindBackingAccounts(BackingAccounts backingAccounts);

	@Binds
	@Singleton
	public abstract BackingStore<TokenID, MerkleToken> bindBackingTokens(BackingTokens backingTokens);

	@Provides
	@Singleton
	public static HederaLedger provideHederaLedger(
			TokenStore tokenStore,
			ScheduleStore scheduleStore,
			EntityCreator creator,
			EntityIdSource ids,
			OptionValidator validator,
			UniqTokenViewsManager uniqTokenViewsManager,
			AccountRecordsHistorian recordsHistorian,
			GlobalDynamicProperties dynamicProperties,
			BackingStore<AccountID, MerkleAccount> backingAccounts,
			BackingStore<TokenID, MerkleToken> backingTokens
	) {
		TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger =
				new TransactionalLedger<>(
						AccountProperty.class,
						MerkleAccount::new,
						backingAccounts,
						new ChangeSummaryManager<>());
		TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokensLedger =
				new TransactionalLedger<>(
						TokenProperty.class,
						MerkleToken::new,
						backingTokens,
						new ChangeSummaryManager<>());
		final var ledger = new HederaLedger(
				tokenStore,
				ids,
				creator,
				validator,
				recordsHistorian,
				dynamicProperties,
				accountsLedger,
				tokensLedger);
		ledger.setTokenViewsManager(uniqTokenViewsManager);
		scheduleStore.setAccountsLedger(accountsLedger);
		scheduleStore.setHederaLedger(ledger);
		return ledger;
	}
}
