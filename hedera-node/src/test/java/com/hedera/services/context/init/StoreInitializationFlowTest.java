package com.hedera.services.context.init;

import com.hedera.services.ledger.accounts.BackingStore;
import com.hedera.services.state.StateAccessor;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.merkle.MerkleUniqueTokenId;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.store.tokens.views.UniqTokenViewsManager;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.fcmap.FCMap;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
class StoreInitializationFlowTest {
	@Mock
	private TokenStore tokenStore;
	@Mock
	private ScheduleStore scheduleStore;
	@Mock
	private StateAccessor stateAccessor;
	@Mock
	private UniqTokenViewsManager uniqTokenViewsManager;
	@Mock
	private BackingStore<AccountID, MerkleAccount> backingAccounts;
	@Mock
	private BackingStore<NftId, MerkleUniqueToken> backingNfts;
	@Mock
	private BackingStore<Pair<AccountID, TokenID>, MerkleTokenRelStatus> backingTokenRels;
	@Mock
	private FCMap<MerkleEntityId, MerkleToken> tokens;
	@Mock
	private FCMap<MerkleUniqueTokenId, MerkleUniqueToken> nfts;

	private StoreInitializationFlow subject;

	@BeforeEach
	void setUp() {
		subject = new StoreInitializationFlow(
				tokenStore,
				scheduleStore,
				stateAccessor,
				uniqTokenViewsManager,
				backingAccounts,
				backingNfts,
				backingTokenRels);
	}

	@Test
	void initsAsExpected() {
		given(stateAccessor.tokens()).willReturn(tokens);
		given(stateAccessor.uniqueTokens()).willReturn(nfts);

		// when:
		subject.run();

		// then:
		verify(backingTokenRels).rebuildFromSources();
		verify(backingAccounts).rebuildFromSources();
		verify(backingNfts).rebuildFromSources();
		verify(tokenStore).rebuildViews();
		verify(scheduleStore).rebuildViews();
		verify(uniqTokenViewsManager).rebuildNotice(tokens, nfts);
	}
}