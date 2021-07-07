package com.hedera.services.store.models;

/*
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Encapsulates the changes of {@link UniqueToken} ownership within the context of one Transaction
 */
public class OwnershipTracker {
	private Map<Id, List<Change>> changes = new HashMap<>();

	public void add(Id token, Change change) {
		if (changes.containsKey(token)) {
			changes.get(token).add(change);
		} else {
			var changeList = new ArrayList<Change>();
			changeList.add(change);
			changes.put(token, changeList);
		}
	}

	public Map<Id, List<Change>> getChanges() {
		return changes;
	}

	public boolean isEmpty() {
		return changes.isEmpty();
	}

	public static Change forMinting(Id treasury, long serialNumber) {
		var change = new Change();
		change.setPreviousOwner(Id.DEFAULT);
		change.setNewOwner(treasury);
		change.setSerialNumber(serialNumber);
		return change;
	}

	public static Change forRemoving(Id accountId, long serialNumber) {
		var change = new Change();
		change.setPreviousOwner(accountId);
		change.setNewOwner(Id.DEFAULT);
		change.setSerialNumber(serialNumber);
		return change;
	}

	/**
	 * Encapsulates one set of Change of a given {@link UniqueToken}
	 */
	public static class Change {
		private Id previousOwner;
		private Id newOwner;
		private long serialNumber;

		public Change(Id previousOwner, Id newOwner, long serialNumber) {
			this.previousOwner = previousOwner;
			this.newOwner = newOwner;
			this.serialNumber = serialNumber;
		}

		public Change() {
		}

		public Id getPreviousOwner() {
			return previousOwner;
		}

		public void setPreviousOwner(Id previousOwner) {
			this.previousOwner = previousOwner;
		}

		public Id getNewOwner() {
			return newOwner;
		}

		public void setNewOwner(Id newOwner) {
			this.newOwner = newOwner;
		}

		public long getSerialNumber() {
			return serialNumber;
		}

		public void setSerialNumber(long serialNumber) {
			this.serialNumber = serialNumber;
		}
	}
}
