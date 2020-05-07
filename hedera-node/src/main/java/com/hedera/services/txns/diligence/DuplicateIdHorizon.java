package com.hedera.services.txns.diligence;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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
import com.hederahashgraph.api.proto.java.TransactionID;

import java.util.Objects;

/**
 * Provides a process object useful for tracking the horizon for
 * which a given {@link TransactionID} is known to be a duplicate.
 *
 * @author Michael Tinker
 */
public class DuplicateIdHorizon implements Comparable<DuplicateIdHorizon> {
	private final long horizon;
	private final TransactionID txnId;

	public DuplicateIdHorizon(long horizon, TransactionID txnId) {
		this.txnId = txnId;
		this.horizon = horizon;
	}

	public long getHorizon() {
		return horizon;
	}

	public TransactionID getTxnId() {
		return txnId;
	}

	@Override
	public int compareTo(DuplicateIdHorizon that) {
		return Long.compare(this.horizon, that.horizon);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!o.getClass().equals(DuplicateIdHorizon.class)) {
			return false;
		}
		DuplicateIdHorizon that = (DuplicateIdHorizon)o;
		return Objects.equals(this.txnId, that.txnId) && (this.horizon == that.horizon);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(DuplicateIdHorizon.class)
				.add("txnId", txnId)
				.add("horizon", horizon)
				.toString();
	}
}
