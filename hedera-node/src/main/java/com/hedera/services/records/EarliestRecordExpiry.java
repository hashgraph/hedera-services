package com.hedera.services.records;

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
import com.hederahashgraph.api.proto.java.AccountID;

import static com.hedera.services.utils.EntityIdUtils.readableId;

import java.util.Objects;

/**
 * Provides a process object useful for tracking the time of the
 * earliest-expiring record associated to an {@link AccountID}.
 *
 * @author Michael Tinker
 */
public class EarliestRecordExpiry implements Comparable<EarliestRecordExpiry> {
	private final long earliestExpiry;
	private final AccountID id;

	public EarliestRecordExpiry(long earliestExpiry, AccountID id) {
		this.earliestExpiry = earliestExpiry;
		this.id = id;
	}

	public long getEarliestExpiry() {
		return earliestExpiry;
	}

	public AccountID getId() {
		return id;
	}

	@Override
	public int compareTo(EarliestRecordExpiry that) {
		return Long.compare(this.earliestExpiry, that.earliestExpiry);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || !o.getClass().equals(EarliestRecordExpiry.class)) {
			return false;
		}
		EarliestRecordExpiry that = (EarliestRecordExpiry) o;
		return Objects.equals(this.id, that.id) && (this.earliestExpiry == that.earliestExpiry);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(EarliestRecordExpiry.class)
				.add("id", readableId(id))
				.add("earliestExpiry", earliestExpiry)
				.toString();
	}
}
