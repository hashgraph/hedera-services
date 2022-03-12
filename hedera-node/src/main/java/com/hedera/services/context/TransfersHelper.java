package com.hedera.services.context;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.util.Arrays;

public class TransfersHelper {
	private TransfersHelper() {
		throw new UnsupportedOperationException("Utility class");
	}

	public static Triple<long[], long[], Integer> updateFungibleChanges(
			final long account,
			final long amount,
			long[] changedAccounts,
			long[] balanceChanges,
			int numChanges) {
		int loc = 0;
		int diff = -1;
		for (; loc < numChanges; loc++) {
			diff = Long.compare(account, changedAccounts[loc]);
			if (diff <= 0) {
				break;
			}
		}
		if (diff == 0) {
			final var currentAmount = balanceChanges[loc];
			balanceChanges[loc] = currentAmount + amount;
		} else {
			if (numChanges == balanceChanges.length) {
				balanceChanges = Arrays.copyOf(balanceChanges, balanceChanges.length * 2);
				changedAccounts = Arrays.copyOf(changedAccounts, changedAccounts.length * 2);
			}
			if (loc == numChanges) {
				balanceChanges[numChanges] = amount;
				changedAccounts[numChanges] = account;
			} else {
				balanceChanges = ArrayUtils.insert(loc, balanceChanges, amount);
				changedAccounts = ArrayUtils.insert(loc, changedAccounts, account);
			}
			numChanges++;
		}
		return Triple.of(balanceChanges, changedAccounts, numChanges);
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
