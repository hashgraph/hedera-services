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

	private int i;

	public BalanceChangeManager(List<BalanceChange> changesSoFar, int numHbar) {
		i = numHbar;
		this.changesSoFar = changesSoFar;
	}

	public void includeChange(BalanceChange change) {
		throw new AssertionError("Not implemented!");
	}

	public BalanceChange nextAssessableChange() {
		throw new AssertionError("Not implemented!");
	}

	public int changesSoFar() {
		throw new AssertionError("Not implemented!");
	}

	public BalanceChange changeFor(Id account, Id denomination) {
		throw new AssertionError("Not implemented!");
	}
}
