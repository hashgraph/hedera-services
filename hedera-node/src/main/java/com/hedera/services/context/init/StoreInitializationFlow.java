package com.hedera.services.context.init;

import com.hedera.services.ledger.accounts.BackingStore;
import com.hedera.services.state.StateAccessor;
import com.hedera.services.state.annotations.WorkingState;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.store.tokens.views.UniqTokenViewsManager;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class StoreInitializationFlow {
	private static final Logger log = LogManager.getLogger(StoreInitializationFlow.class);

	private final TokenStore tokenStore;
	private final ScheduleStore scheduleStore;
	private final StateAccessor stateAccessor;
	private final UniqTokenViewsManager uniqTokenViewsManager;
	private final BackingStore<AccountID, MerkleAccount> backingAccounts;
	private final BackingStore<NftId, MerkleUniqueToken> backingNfts;
	private final BackingStore<Pair<AccountID, TokenID>, MerkleTokenRelStatus> backingTokenRels;

	@Inject
	public StoreInitializationFlow(
			TokenStore tokenStore,
			ScheduleStore scheduleStore,
			@WorkingState StateAccessor stateAccessor,
			UniqTokenViewsManager uniqTokenViewsManager,
			BackingStore<AccountID, MerkleAccount> backingAccounts,
			BackingStore<NftId, MerkleUniqueToken> backingNfts,
			BackingStore<Pair<AccountID, TokenID>, MerkleTokenRelStatus> backingTokenRels
	) {
		this.tokenStore = tokenStore;
		this.scheduleStore = scheduleStore;
		this.backingAccounts = backingAccounts;
		this.stateAccessor = stateAccessor;
		this.backingNfts = backingNfts;
		this.backingTokenRels = backingTokenRels;
		this.uniqTokenViewsManager = uniqTokenViewsManager;
	}

	public void run() {
		backingTokenRels.rebuildFromSources();
		backingAccounts.rebuildFromSources();
		backingNfts.rebuildFromSources();
		log.info("Backing stores rebuilt");

		tokenStore.rebuildViews();
		scheduleStore.rebuildViews();
		log.info("Store internal views rebuilt");

		uniqTokenViewsManager.rebuildNotice(stateAccessor.tokens(), stateAccessor.uniqueTokens());
		log.info("Unique token views rebuilt");
	}
}
