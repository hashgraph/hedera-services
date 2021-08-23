package com.hedera.services.state.expiry.backgroundworker.buckets;

/*
 * -
 * ‌
 * Hedera Services Node
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *       http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Represents a logical group of jobs which can be executed either pre-tx (atomic) and post-tx(non-atomic).
 */
public interface JobBucket {

	/**
	 * Pre-transaction jobs are typically affecting lightweight entities, which are expired and should be removed from state.
	 * The entities are either transaction records or expired scheduled transactions.
	 *
	 * @param now - the consensus timestamp
	 */
	default void doPreTransactionJobs(long now) {
	}


	/**
	 * Post-transaction jobs are typically affecting heavyweight entities, like expired tokens or accounts, which can
	 * carry additional complexities upon deletion. Some of those changes cannot be atomic -
	 * like returning batch of unique tokens to the treasury.
	 *
	 * @param now - the consensus timestamp
	 */
	default void doPostTransactionJobs(long now) {}
}
