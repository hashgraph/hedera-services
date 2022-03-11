package com.hedera.services.context;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TransfersHelper {
	public static Pair<long[], long[]> updateFungibleChanges(final long account, final long amount,
			long[] changedAccounts,
			long[] balanceChanges) {
		int loc = 0;
		int diff = -1;
		assertEquals(changedAccounts.length, balanceChanges.length);
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
				balanceChanges = ArrayUtils.add(balanceChanges, amount);
				changedAccounts = ArrayUtils.add(changedAccounts, account);
			} else {
				balanceChanges = ArrayUtils.add(balanceChanges, loc, amount);
				changedAccounts = ArrayUtils.add(changedAccounts, loc, account);
			}
		}
		return Pair.of(balanceChanges, changedAccounts);
	}

	public static Pair<long[], long[]> purgeZeroAdjustments(long[] balanceChanges, long[] changedAccounts) {
		int lastZeroRemoved;
		do {
			lastZeroRemoved = -1;
			for (int i = 0; i < changedAccounts.length; i++) {
				if (balanceChanges[i] == 0) {
					balanceChanges = ArrayUtils.remove(balanceChanges, i);
					changedAccounts = ArrayUtils.remove(changedAccounts, i);
					lastZeroRemoved = i;
					break;
				}
			}
		} while (lastZeroRemoved != -1);
		return Pair.of(balanceChanges, changedAccounts);
	}

}
