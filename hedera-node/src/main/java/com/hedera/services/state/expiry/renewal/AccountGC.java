package com.hedera.services.state.expiry.renewal;

import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.ledger.backing.BackingStore;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.CurrencyAdjustments;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;

import javax.inject.Singleton;
import java.util.Collections;
import java.util.List;

@Singleton
public class AccountGC {
	private final AliasManager aliasManager;
	private final SigImpactHistorian sigImpactHistorian;
	private final BackingStore<AccountID, MerkleAccount> backingAccounts;

	public AccountGC(
			final AliasManager aliasManager,
			final SigImpactHistorian sigImpactHistorian,
			final BackingStore<AccountID, MerkleAccount> backingAccounts
	) {
		this.aliasManager = aliasManager;
		this.sigImpactHistorian = sigImpactHistorian;
		this.backingAccounts = backingAccounts;
	}

	public TreasuryReturns expireBestEffort(final EntityNum num, final MerkleAccount account) {
		List<EntityId> tokenTypes = Collections.emptyList();
		List<CurrencyAdjustments> returnTransfers = Collections.emptyList();
		final var accountTokens = account.tokens();
		final var grpcId = num.toGrpcAccountId();
		if (accountTokens.numAssociations() > 0) {
//			tokenTypes = new ArrayList<>();
//			returnTransfers = new ArrayList<>();
			for (final var tokenId : accountTokens.asTokenIds()) {
//				doReturnToTreasury(grpcId, tokenId, tokenTypes, returnTransfers, curTokens);
			}
		}

		// Remove the entry from auto created accounts map if there is an entry in the map
		if (aliasManager.forgetAlias(account.getAlias())) {
			sigImpactHistorian.markAliasChanged(account.getAlias());
		}

		backingAccounts.remove(num.toGrpcAccountId());
		sigImpactHistorian.markEntityChanged(num.longValue());

		return new TreasuryReturns(tokenTypes, returnTransfers, true);
	}
}
