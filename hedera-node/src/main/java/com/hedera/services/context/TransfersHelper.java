package com.hedera.services.context;

import org.apache.commons.lang3.ArrayUtils;

public class TransfersHelper {
	public static void updateFungibleChanges(final long account, final long amount, long[] changedAccounts,
			long[] balanceChanges) {
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
				ArrayUtils.add(balanceChanges, amount);
				ArrayUtils.add(changedAccounts, account);
			} else {
				balanceChanges[loc] = amount;
				changedAccounts[loc] = account;
			}
		}
	}

	public static void purgeZeroAdjustments(final long[] changedAccounts, final long[] balanceChanges) {
		int lastZeroRemoved;
		do {
			lastZeroRemoved = -1;
			for (int i = 0; i < changedAccounts.length; i++) {
				if (balanceChanges[i] == 0) {
					ArrayUtils.remove(balanceChanges, i);
					ArrayUtils.remove(changedAccounts, i);
					lastZeroRemoved = i;
					break;
				}
			}
		} while (lastZeroRemoved != -1);
	}
}
