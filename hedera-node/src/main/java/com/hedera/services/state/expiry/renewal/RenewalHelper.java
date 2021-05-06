package com.hedera.services.state.expiry.renewal;

import com.hedera.services.config.HederaNumbers;
import com.hedera.services.exceptions.NegativeAccountBalanceException;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.swirlds.fcmap.FCMap;

import java.util.function.Supplier;

import static com.hedera.services.state.expiry.renewal.ExpiredEntityClassification.ACCOUNT_EXPIRED_NONZERO_BALANCE;
import static com.hedera.services.state.expiry.renewal.ExpiredEntityClassification.ACCOUNT_EXPIRED_ZERO_BALANCE;
import static com.hedera.services.state.expiry.renewal.ExpiredEntityClassification.OTHER;

/**
 * Helper for renewing and removing expired entities. Only crypto accounts are supported in this implementation.
 */
public class RenewalHelper {
	private final long shard, realm;
	private final Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts;

	private MerkleAccount lastClassifiedAccount = null;
	private MerkleEntityId lastClassifiedEntityId;

	public RenewalHelper(HederaNumbers hederaNumbers, Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts) {
		this.shard = hederaNumbers.shard();
		this.realm = hederaNumbers.realm();
		this.accounts = accounts;
	}

	public ExpiredEntityClassification classify(long candidateNum, long now) {
		lastClassifiedEntityId = new MerkleEntityId(shard, realm, candidateNum);
		var currentAccounts = accounts.get();

		if (!currentAccounts.containsKey(lastClassifiedEntityId)) {
			return OTHER;
		} else {
			lastClassifiedAccount = currentAccounts.get(lastClassifiedEntityId);
			if (lastClassifiedAccount.getExpiry() > now) {
				return OTHER;
			}
			return lastClassifiedAccount.getBalance() > 0
					? ACCOUNT_EXPIRED_NONZERO_BALANCE
					: ACCOUNT_EXPIRED_ZERO_BALANCE;
		}
	}

	public void removeLastClassifiedEntity() {
		assertHasLastClassifiedAccount();
		if (lastClassifiedAccount.getBalance() > 0) {
			throw new IllegalStateException("Cannot remove the last classified account, has non-zero balance!");
		}
		accounts.get().remove(lastClassifiedEntityId);
	}

	public void renewLastClassifiedWith(long fee, long renewalPeriod) {
		assertHasLastClassifiedAccount();
		assertLastClassifiedAccountCanAfford(fee);

		final var currentAccounts = accounts.get();

		final var mutableLastClassified = currentAccounts.getForModify(lastClassifiedEntityId);
		final long newExpiry = mutableLastClassified.getExpiry() + renewalPeriod;
		final long newBalance = mutableLastClassified.getBalance() - fee;
		mutableLastClassified.setExpiry(newExpiry);
		try {
			mutableLastClassified.setBalance(newBalance);
		} catch (NegativeAccountBalanceException impossible) { }

		currentAccounts.replace(lastClassifiedEntityId, mutableLastClassified);
	}

	public MerkleAccount getLastClassifiedAccount() {
		return lastClassifiedAccount;
	}

	private void assertHasLastClassifiedAccount() {
		if (lastClassifiedAccount == null) {
			throw new IllegalStateException("Cannot remove a last classified account; none is present!");
		}
	}

	private void assertLastClassifiedAccountCanAfford(long fee) {
		if (lastClassifiedAccount.getBalance() < fee) {
			var msg = "Cannot charge " + fee + " to " + lastClassifiedEntityId.toAbbrevString() + "!";
			throw new IllegalStateException(msg);
		}
	}
}
