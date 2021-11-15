package com.hedera.services.context;

import com.hedera.services.store.models.NftId;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransferList;

import javax.inject.Inject;
import javax.inject.Singleton;

import static com.hedera.services.ledger.HederaLedger.ACCOUNT_ID_COMPARATOR;

@Singleton
public class SideEffectsTracker {
	private static final int MAX_CONCEIVABLE_TOKENS_PER_TXN = 1_000;

	private final TransferList.Builder netHbarChanges = TransferList.newBuilder();

	@Inject
	public SideEffectsTracker() {
		/* For Dagger2 */
	}

	/**
	 * Clears all the side effects tracked since the last call to {@link SideEffectsTracker#reset()},
	 * or since this instance was constructed.
	 */
	public void reset() {
		netHbarChanges.clear();
	}

	/**
	 * Tracks an incremental ℏ balance change for the given account. It is important to emphasize that each
	 * change is <b>incremental</b>; that is, two consecutive calls {@code hbarChange(0.0.12345, +1)} and
	 * {@code hbarChange(0.0.12345, +2)} are equivalent to a single {@code hbarChange(0.0.12345, +3)} call.
	 *
	 * @param account the changed account
	 * @param amount the incremental ℏ change to track
	 */
	public void hbarChange(final AccountID account, final long amount) {
		int loc = 0;
		int diff = -1;
		final var hbarChanges = netHbarChanges.getAccountAmountsBuilderList();
		for (; loc < hbarChanges.size(); loc++) {
			diff = ACCOUNT_ID_COMPARATOR.compare(account, hbarChanges.get(loc).getAccountID());
			if (diff <= 0) {
				break;
			}
		}
		if (diff == 0) {
			var aa = hbarChanges.get(loc);
			long current = aa.getAmount();
			aa.setAmount(current + amount);
		} else {
			if (loc == hbarChanges.size()) {
				netHbarChanges.addAccountAmounts(aaBuilderWith(account, amount));
			} else {
				netHbarChanges.addAccountAmounts(loc, aaBuilderWith(account, amount));
			}
		}
	}

	public void tokenUnitsChange(final TokenID token, final AccountID account, final long amount) {
		throw new AssertionError("Not implemented");
	}

	public void nftOwnerChange(final NftId nftId, final AccountID from, AccountID to) {
		throw new AssertionError("Not implemented");
	}

	public TransferList computeNetHbarChanges() {
		purgeZeroAdjustments(netHbarChanges);
		return netHbarChanges.build();
	}

	/* --- Internal helpers --- */
	private void purgeZeroAdjustments(final TransferList.Builder changes) {
		int lastZeroRemoved;
		do {
			lastZeroRemoved = -1;
			for (int i = 0; i < changes.getAccountAmountsCount(); i++) {
				if (changes.getAccountAmounts(i).getAmount() == 0) {
					changes.removeAccountAmounts(i);
					lastZeroRemoved = i;
					break;
				}
			}
		} while (lastZeroRemoved != -1);
	}

	private AccountAmount.Builder aaBuilderWith(final AccountID account, final long amount) {
		return AccountAmount.newBuilder().setAccountID(account).setAmount(amount);
	}
}
