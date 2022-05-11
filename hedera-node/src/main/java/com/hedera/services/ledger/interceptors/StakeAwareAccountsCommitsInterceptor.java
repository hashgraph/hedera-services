package com.hedera.services.ledger.interceptors;

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.ledger.EntityChangeSet;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;

import java.util.ArrayList;
import java.util.List;

public class StakeAwareAccountsCommitsInterceptor extends AccountsCommitInterceptor {
	// Map of changed stakedAccounts and the effective change on its stakedToMe
	private final List<StakeImpact> stakeChanges;
	private final StakedAccountsImpactManager stakedAccountsManager;

	public StakeAwareAccountsCommitsInterceptor(final SideEffectsTracker sideEffectsTracker,
			final StakedAccountsImpactManager manager) {
		super(sideEffectsTracker);
		stakeChanges = new ArrayList<>();
		stakedAccountsManager = manager;
	}

	@Override
	public void preview(final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges) {
		final var n = pendingChanges.size();
		if (n == 0) {
			return;
		}
		super.preview(pendingChanges);
		stakeChanges.clear();

		for (int i = 0; i < n; i++) {
			final var entity = pendingChanges.entity(i);
			final var change = pendingChanges.changes(i);
			if (entity != null && entity.getStakedId() > 0 && change != null) {
				final var stakedId = EntityNum.fromLong(entity.getStakedId());
				if (change.containsKey(AccountProperty.BALANCE)) {
					final long newBalance = (long) change.get(AccountProperty.BALANCE);
					final long adjustment = (entity != null) ? newBalance - entity.getBalance() : newBalance;
					stakeChanges.add(new StakeImpact(stakedId, adjustment));
				}

				if (change.containsKey(AccountProperty.STAKED_ID)) {
					final var newStakeId = (long) change.get(AccountProperty.STAKED_ID);
					stakedAccountsManager.updateStakeId(stakeChanges, entity, newStakeId);
				}
			}
		}
	}

	@Override
	public void finalizeSideEffects() {
		stakedAccountsManager.aggregateAndCommitStakeImpacts(stakeChanges);
	}
}
