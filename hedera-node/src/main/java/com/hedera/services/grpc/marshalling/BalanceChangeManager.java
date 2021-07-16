package com.hedera.services.grpc.marshalling;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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

import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.store.models.Id;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BalanceChangeManager {
	private final List<BalanceChange> changesSoFar;
	private final Map<Pair<Id, Id>, BalanceChange> indexedChanges = new HashMap<>();

	private int nextCandidateChange;

	interface ChangeManagerFactory {
		BalanceChangeManager from(List<BalanceChange> changesSoFar, int numHbar);
	}

	public BalanceChangeManager(List<BalanceChange> changesSoFar, int numHbar) {
		nextCandidateChange = numHbar;
		this.changesSoFar = changesSoFar;
		changesSoFar.forEach(this::index);
	}

	public int nestingLevel() {
		throw new AssertionError("Not implemented!");
	}

	public void includeChange(BalanceChange change) {
		changesSoFar.add(change);
		index(change);
	}

	public BalanceChange nextTriggerCandidate() {
		final var numChanges = changesSoFar.size();
		if (nextCandidateChange == numChanges) {
			return null;
		}
		while (nextCandidateChange < numChanges) {
			final var candidate = changesSoFar.get(nextCandidateChange);
			nextCandidateChange++;
			if (couldTriggerCustomFees(candidate)) {
				return candidate;
			}
		}
		return null;
	}

	private boolean couldTriggerCustomFees(BalanceChange candidate) {
		return candidate.isForNft() || (!candidate.isForHbar()  && candidate.units() < 0);
	}

	public int changesSoFar() {
		return changesSoFar.size();
	}

	public BalanceChange changeFor(Id account, Id denom) {
		return indexedChanges.get(Pair.of(account, denom));
	}

	List<BalanceChange> allChanges() {
		return changesSoFar;
	}

	private void index(BalanceChange change) {
		if (!change.isForNft()) {
			if (change.isForHbar()) {
				if (indexedChanges.put(Pair.of(change.getAccount(), Id.MISSING_ID), change) != null) {
					throw new IllegalArgumentException("Duplicate balance change :: " + change);
				}
			} else {
				if (indexedChanges.put(Pair.of(change.getAccount(), change.getToken()), change) != null) {
					throw new IllegalArgumentException("Duplicate balance change :: " + change);
				}
			}
		}
	}
}
