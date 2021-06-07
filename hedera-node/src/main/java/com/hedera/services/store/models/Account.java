package com.hedera.services.store.models;

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

import com.google.common.base.MoreObjects;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * Encapsulates the state and operations of a Hedera account.
 *
 * Operations are validated, and throw a {@link com.hedera.services.exceptions.InvalidTransactionException}
 * with response code capturing the failure when one occurs.
 *
 * <b>NOTE:</b> This implementation is incomplete, and includes
 * only the API needed to support the Hedera Token Service. The
 * memo field, for example, is not yet present.
 */
public class Account {
	private final Id id;

	private long expiry;
	private long balance;
	private boolean deleted = false;

	public Account(Id id) {
		this.id = id;
	}

	public void setExpiry(long expiry) {
		this.expiry = expiry;
	}

	public void initBalance(long balance) {
		this.balance = balance;
	}

	public Id getId() {
		return id;
	}

	/* NOTE: The object methods below are only overridden to improve
	readability of unit tests; model objects are not used in hash-based
	collections, so the performance of these methods doesn't matter. */
	@Override
	public boolean equals(Object obj) {
		return EqualsBuilder.reflectionEquals(this, obj);
	}

	@Override
	public int hashCode() {
		return HashCodeBuilder.reflectionHashCode(this);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(Account.class)
				.add("id", id)
				.add("expiry", expiry)
				.add("balance", balance)
				.add("deleted", deleted)
				.toString();
	}
}
