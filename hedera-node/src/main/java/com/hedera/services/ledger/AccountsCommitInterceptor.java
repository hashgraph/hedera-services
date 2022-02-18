package com.hedera.services.ledger;

import com.google.protobuf.ByteString;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleAccountTokens;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Singleton
public class AccountsCommitInterceptor implements
		CommitInterceptor<AccountID, MerkleAccount, AccountProperty> {

	// The tracker this interceptor should use for previewing changes. The interceptor is NOT
	// responsible for calling reset() on the tracker, as that will be done by the client code.
	private final SideEffectsTracker sideEffectsTracker;

	public AccountsCommitInterceptor(final SideEffectsTracker sideEffectsTracker) {
		this.sideEffectsTracker = sideEffectsTracker;
	}

	/**
	 * Accepts a pending change set, including creations and removals.
	 *
	 * @throws IllegalStateException if these changes are invalid
	 */
	@Override
	public void preview(final List<MerkleLeafChanges<AccountID, MerkleAccount, AccountProperty>> changesToCommit) {
		final List<Long> balances = new ArrayList<>();
		for (final var changeToCommit : changesToCommit) {
			final var account = changeToCommit.id();
			final var merkleAccount = changeToCommit.merkleLeaf();
			final var changedProperties = changeToCommit.changes();

			if (merkleAccount == null) {
				trackAutoCreation(changedProperties, account);
				continue;
			}

			trackHBarTransfer(changedProperties, account, merkleAccount, balances);
			trackAutoAssociationBetweenTokenAndAccount(changedProperties, account);
			trackNFTUnitsChange(changesToCommit, changedProperties, account, merkleAccount);
		}

		doZeroSum(balances);
	}

	private void trackAutoCreation(final Map<AccountProperty, Object> changedProperties, final AccountID account) {
		final var isContract = changedProperties.containsKey(AccountProperty.IS_SMART_CONTRACT) && (boolean) changedProperties.get(AccountProperty.IS_SMART_CONTRACT);
		final var alias =
				changedProperties.containsKey(AccountProperty.ALIAS) ?
						(ByteString) changedProperties.get(AccountProperty.ALIAS) : ByteString.EMPTY;
		if(!isContract) {
			sideEffectsTracker.trackAutoCreation(account, alias);
		}
	}

	private void trackHBarTransfer(final Map<AccountProperty, Object> changedProperties,
								   final AccountID account, final MerkleAccount merkleAccount,
								   final List<Long> balances) {
		if (changedProperties.containsKey(AccountProperty.BALANCE)) {
			final long balanceChange =
					(long) changedProperties.get(AccountProperty.BALANCE) - merkleAccount.getBalance();
			balances.add(balanceChange);

			sideEffectsTracker.trackHbarChange(account, balanceChange);
		}
	}

	private void trackAutoAssociationBetweenTokenAndAccount(final Map<AccountProperty, Object> changedProperties,
														  final AccountID account) {
		if (changedProperties.containsKey(AccountProperty.TOKENS)) {
			final var tokens =
					((MerkleAccountTokens) changedProperties.get(AccountProperty.TOKENS)).asTokenIds();

			for (final var token : tokens) {
				if (changedProperties.containsKey(AccountProperty.ALREADY_USED_AUTOMATIC_ASSOCIATIONS)) {
					sideEffectsTracker.trackAutoAssociation(token, account);
				}
			}
		}
	}

	private void trackNFTUnitsChange(final List<MerkleLeafChanges<AccountID, MerkleAccount, AccountProperty>> changesToCommit,
									 final Map<AccountProperty, Object> changedProperties,
									 final AccountID account, final MerkleAccount merkleAccount) {
		if (changedProperties.containsKey(AccountProperty.NUM_NFTS_OWNED) && getTokenIdForNFTTransfer(changesToCommit).isPresent()) {
			final var nftsUnitsChange =
					merkleAccount.getNftsOwned() - (long) changedProperties.get(AccountProperty.NUM_NFTS_OWNED);
			sideEffectsTracker.trackTokenUnitsChange(getTokenIdForNFTTransfer(changesToCommit).get(), account,
					nftsUnitsChange);
		}
	}

	private Optional<TokenID> getTokenIdForNFTTransfer(final List<MerkleLeafChanges<AccountID, MerkleAccount,
			AccountProperty>> changesToCommit) {
		for (final var changeToCommit : changesToCommit) {
			if (changeToCommit.changes().containsKey(AccountProperty.TOKENS)) {
				final var tokens =
						((MerkleAccountTokens) changeToCommit.changes().get(AccountProperty.TOKENS)).asTokenIds();
				return tokens.size() == 1 ? Optional.of(tokens.get(0)) : Optional.empty();
			}
		}

		return Optional.empty();
	}

	private void doZeroSum(final List<Long> balances) {
		if (balances.size() > 1) {
			final var sum = getArrayLongSum(balances);

			if (sum != 0) {
				throw new IllegalStateException("Invalid balance changes!");
			}
		}
	}

	private long getArrayLongSum(final List<Long> balances) {
		int sum = 0;
		for (long value : balances) {
			sum += value;
		}
		return sum;
	}
}