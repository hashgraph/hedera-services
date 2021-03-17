package com.hedera.services.usage.schedule;

/*-
 * ‌
 * Hedera Services API Fees
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

import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.SchedulableTransactionBody;

import java.nio.charset.StandardCharsets;

import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_RICH_INSTANT_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.getAccountKeyStorageSize;

public class ExtantScheduleContext {
	private final int numSigners;
	private final Key adminKey;
	private final String memo;
	private final boolean resolved;
	private final SchedulableTransactionBody scheduledTxn;

	private ExtantScheduleContext(ExtantScheduleContext.Builder builder) {
		resolved = builder.resolved;
		numSigners = builder.numSigners;
		memo = builder.memo;
		adminKey = builder.adminKey;
		scheduledTxn = builder.scheduledTxn;
	}

	public long nonBaseRb() {
		return BASIC_ENTITY_ID_SIZE
				+ (resolved ? BASIC_RICH_INSTANT_SIZE : 0)
				+ memo.getBytes(StandardCharsets.UTF_8).length
				+ getAccountKeyStorageSize(adminKey)
				+ scheduledTxn.getSerializedSize();
	}

	public Key adminKey() {
		return adminKey;
	}

	public int numSigners() {
		return numSigners;
	}

	public String memo() {
		return memo;
	}

	public boolean isResolved() {
		return resolved;
	}

	public SchedulableTransactionBody scheduledTxn() {
		return scheduledTxn;
	}

	public static ExtantScheduleContext.Builder newBuilder() {
		return new ExtantScheduleContext.Builder();
	}

	public static class Builder {
		private static final int IS_RESOLVED_MASK = 1 << 0;
		private static final int SCHEDULED_TXN_MASK = 1 << 1;
		private static final int MEMO_MASK = 1 << 2;
		private static final int ADMIN_KEY_MASK = 1 << 3;
		private static final int NUM_SIGNERS_MASK = 1 << 4;

		private static final int ALL_FIELDS_MASK = NUM_SIGNERS_MASK
				| SCHEDULED_TXN_MASK
				| MEMO_MASK
				| ADMIN_KEY_MASK
				| IS_RESOLVED_MASK;
		private int mask = 0;

		private int numSigners;
		private Key adminKey;
		private String memo;
		private boolean resolved;
		private SchedulableTransactionBody scheduledTxn;

		private Builder() {}

		public ExtantScheduleContext build() {
			if (mask != ALL_FIELDS_MASK) {
				throw new IllegalStateException(String.format("Field mask is %d, not %d!", mask, ALL_FIELDS_MASK));
			}
			return new ExtantScheduleContext(this);
		}

		public ExtantScheduleContext.Builder setNumSigners(int numSigners) {
			this.numSigners = numSigners;
			mask |= NUM_SIGNERS_MASK;
			return this;
		}

		public ExtantScheduleContext.Builder setScheduledTxn(SchedulableTransactionBody scheduledTxn) {
			this.scheduledTxn = scheduledTxn;
			mask |= SCHEDULED_TXN_MASK;
			return this;
		}

		public ExtantScheduleContext.Builder setMemo(String memo) {
			this.memo = memo;
			mask |= MEMO_MASK;
			return this;
		}

		public ExtantScheduleContext.Builder setAdminKey(Key adminKey) {
			this.adminKey = adminKey;
			mask |= ADMIN_KEY_MASK;
			return this;
		}

		public ExtantScheduleContext.Builder setResolved(boolean flag) {
			this.resolved = flag;
			mask |= IS_RESOLVED_MASK;
			return this;
		}
	}
}
