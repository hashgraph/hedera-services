package com.hedera.services.state.expiry;

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
import com.hedera.services.state.submerkle.ExpirableTxnRecord;

import java.util.ArrayDeque;
import java.util.Deque;

public class MonotonicFullQueueExpiries<K> implements KeyedExpirations<K> {
	long now = 0L;
	Deque<ExpiryEvent> allExpiries = new ArrayDeque<>();

	@Override
	public void track(K id, long expiry) {
		if (expiry < now) {
			throw new IllegalArgumentException(String.format("Track time %d for %s not later than %d", expiry, id, now));
		}
		now = expiry;
		allExpiries.add(new ExpiryEvent(id, expiry));
	}

	@Override
	public boolean hasExpiringAt(long now) {
		return !allExpiries.isEmpty() && allExpiries.peekFirst().isExpiredAt(now);
	}

	@Override
	public K expireNextAt(long now) {
		if (allExpiries.isEmpty()) {
			throw new IllegalStateException("No ids are queued for expiration!");
		}
		if (!allExpiries.peek().isExpiredAt(now)) {
			throw new IllegalArgumentException("Next id is not expired!");
		}
		return allExpiries.removeFirst().getId();
	}

	final class ExpiryEvent {
		private final K id;
		private final long expiry;

		public ExpiryEvent(K id, long expiry) {
			this.id = id;
			this.expiry = expiry;
		}

		public boolean isExpiredAt(long now) {
			return expiry <= now;
		}

		public K getId() {
			return id;
		}

		public long getExpiry() {
			return expiry;
		}

		@Override
		public String toString() {
			return MoreObjects.toStringHelper(ExpiryEvent.class)
					.add("id", id)
					.add("expiry", expiry)
					.toString();
		}
	}
}
