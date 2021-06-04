package com.hedera.services.state.expiry;

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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;

public class PriorityQueueExpiries<K> implements KeyedExpirations<K> {
	private long now = 0L;
	private BlockingQueue<ExpiryEvent> allExpiries = new PriorityBlockingQueue<>();

	@Override
	public void reset() {
		now = 0L;
		allExpiries.clear();
	}

	@Override
	public void track(K id, long expiry) {
		allExpiries.offer(new ExpiryEvent(id, expiry));
		now = allExpiries.peek().getExpiry();
	}

	@Override
	public boolean hasExpiringAt(long now) {
		return !allExpiries.isEmpty() && allExpiries.peek().isExpiredAt(now);
	}

	@Override
	public K expireNextAt(long now) {
		if (allExpiries.isEmpty()) {
			throw new IllegalStateException("No ids are queued for expiration!");
		}
		if (!allExpiries.peek().isExpiredAt(now)) {
			throw new IllegalArgumentException(String.format("Argument 'now=%d' is earlier than the next expiry!",
					now));
		}
		return allExpiries.remove().getId();
	}

	final class ExpiryEvent implements Comparable<ExpiryEvent> {
		private final K id;
		private final long expiry;

		ExpiryEvent(K id, long expiry) {
			this.id = id;
			this.expiry = expiry;
		}

		boolean isExpiredAt(long now) {
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

		@Override
		public int compareTo(ExpiryEvent that) {
			return Long.compare(this.expiry, that.expiry);
		}
	}

	BlockingQueue<ExpiryEvent> getAllExpiries() {
		return allExpiries;
	}

	long getNow() {
		return now;
	}
}
