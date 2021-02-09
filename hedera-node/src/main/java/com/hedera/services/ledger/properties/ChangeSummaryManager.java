package com.hedera.services.ledger.properties;

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

import java.util.EnumMap;

/**
 * Minimal implementation of a helper that manages summary changesets.
 * An extension point for possible future performance optimizations.
 *
 * @param <A> the type of account being changed.
 * @param <P> the property family whose changesets are to be summarized.
 *
 * @author Michael Tinker
 */
public class ChangeSummaryManager<A, P extends Enum<P> & BeanProperty<A>> {
	/**
	 * Updates the changeset summary for the given property to the given value.
	 *
	 * @param changes the total changeset summary so far.
	 * @param property the property in the family whose changeset should be updated.
	 * @param value the new value that summarizes the changeset.
	 */
	public void update(EnumMap<P, Object> changes, P property, Object value) {
		changes.put(property, value);
	}

	/**
	 * Flush a changeset summary to a given object.
	 *
	 * @param changes the summary of changes made to the relevant property family.
	 * @param account the account to receive the net changes.
	 */
	public void persist(EnumMap<P, Object> changes, A account) {
		changes.entrySet().forEach(entry ->
			entry.getKey().setter().accept(account, entry.getValue())
		);
	}
}
