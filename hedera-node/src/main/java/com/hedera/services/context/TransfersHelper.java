package com.hedera.services.context;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;

public class TransfersHelper {
	private TransfersHelper() {
		throw new UnsupportedOperationException("Utility class");
	}

	public static Pair<long[], long[]> updateFungibleChanges(
			final long account,
			final long amount,
			long[] changedAccounts,
			long[] balanceChanges,
			final int numChanges) {
		int loc = 0;
		int diff = -1;
		for (; loc < changedAccounts.length; loc++) {
			diff = Long.compare(account, changedAccounts[loc]);
			if (diff <= 0) {
				break;
			}
		}
		if (diff == 0) {
			final var currentAmount = balanceChanges[loc];
			balanceChanges[loc] = currentAmount + amount;
		} else {
			if (loc == balanceChanges.length) {
				balanceChanges[numChanges] = amount;
				changedAccounts[numChanges] = account;
			} else {
				balanceChanges = ArrayUtils.insert(loc, balanceChanges, amount);
				changedAccounts = ArrayUtils.insert(loc, changedAccounts, account);
			}
		}
		return Pair.of(balanceChanges, changedAccounts);
	}

	public static Pair<long[], long[]> purgeZeroAdjustments(long[] balanceChanges, long[] changedAccounts,
			int numChanges) {
		int lastZeroRemoved;
		do {
			lastZeroRemoved = -1;
			for (int i = 0; i < changedAccounts.length; i++) {
				if (balanceChanges[i] == 0) {
					balanceChanges = ArrayUtils.remove(balanceChanges, i);
					changedAccounts = ArrayUtils.remove(changedAccounts, i);
					numChanges--;
					lastZeroRemoved = i;
					break;
				}
			}
		} while (lastZeroRemoved != -1);
		return Pair.of(balanceChanges, changedAccounts);
	}

}
