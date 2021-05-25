package com.hedera.services.context;

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

import com.swirlds.common.AddressBook;

import java.util.function.Supplier;

/**
 * Summarizes useful information about the nodes in the {@link AddressBook}
 * from the Platform. In the future, there may be events that require
 * re-reading the book; but at present nodes may treat the initializing
 * book as static.
 */
public class NodeInfo {
	private boolean bookIsRead = false;

	private int n;
	private boolean[] isZeroStake;

	private final Supplier<AddressBook> book;

	public NodeInfo(Supplier<AddressBook> book) {
		this.book = book;
	}

	/**
	 * Returns true if the given id refers to a missing node, or a
	 * node in the address book with explicit zero stake.
	 *
	 * @param nodeId the id of interest
	 */
	public boolean isZeroStake(long nodeId) {
		if (!bookIsRead) {
			readBook();
		}

		final int index = (int)nodeId;
		if (index < 0 || index >= n) {
			return true;
		}
		return isZeroStake[index];
	}

	private void readBook() {
		final var staticBook = book.get();

		n = staticBook.getSize();
		isZeroStake = new boolean[n];
		for (int i = 0; i < n; i++) {
			final var address = staticBook.getAddress(i);
			isZeroStake[i] = address.getStake() <= 0;
		}

		bookIsRead = true;
	}
}
