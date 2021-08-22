package com.hedera.services.ledger;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.accounts.BackingAccounts;
import com.hedera.services.ledger.accounts.BackingStore;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.ChangeSummaryManager;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.store.tokens.views.UniqTokenViewsManager;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.AccountID;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;

@Module
public abstract class LedgerModule {
	@Binds
	@Singleton
	public abstract BackingStore<AccountID, MerkleAccount> bindBackingAccounts(BackingAccounts backingAccounts);

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
			BackingStore<AccountID, MerkleAccount> backingAccounts
	) {
		TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger =
				new TransactionalLedger<>(
						AccountProperty.class,
						MerkleAccount::new,
						backingAccounts,
						new ChangeSummaryManager<>());
		final var ledger = new HederaLedger(
				tokenStore,
				ids,
				creator,
				validator,
				recordsHistorian,
				dynamicProperties,
				accountsLedger);
		ledger.setTokenViewsManager(uniqTokenViewsManager);
		scheduleStore.setAccountsLedger(accountsLedger);
		scheduleStore.setHederaLedger(ledger);
		return ledger;
	}
}
