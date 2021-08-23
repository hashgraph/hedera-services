package com.hedera.services.state.expiry.backgroundworker;

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
 * Defines the behaviour of a background worker, although it's not really "background", but rather queued.
 */
public interface BackgroundWorker {

	/**
	 * Runs jobs which can and should be executed before the transaction processing. Affects transaction records
	 * and scheduled transactions.
	 * Those jobs can be processed in the scope of a single transaction, and thus they are atomic.
	 * 
	 * @param now - the effective consensus timestamp. Used to determine expirable entities
	 */
	void runPreTransactionJobs(long now);
	
	/**
	 * Runs jobs which have increased complexity, and cannot be finalized in the scope of 
	 * a single transaction - not atomic. Affects heavy entities like tokens and accounts
	 * in the process of expiry/removal.
	 *
	 * @param now - the effective consensus timestamp. Used to determine expirable entities
	 */
	void runPostTransactionJobs(long now);

}
