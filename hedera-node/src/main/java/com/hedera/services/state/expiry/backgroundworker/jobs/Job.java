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

package com.hedera.services.state.expiry.backgroundworker.jobs;

import com.hedera.services.state.submerkle.EntityId;

/**
 * A job represents a specific set of instructions, required for expiring or auto-renewing entities.
 */
public interface Job {

	JobStatus getStatus();
	
	void setStatus(JobStatus status);

	/**
	 * Used to compare and decide which jobs are with higher priority.
	 * The order of execution should always be:
	 * 1. Lightweight entities, as they are more but easier to handle.
	 * 2. Heavyweight entities, whose jobs were paused (e.g. unfinished transfer of a batch of unique tokens to treasury).
	 * 3. Heavyweight(new) - new heavyweight jobs, which have been recently created and will be started for the first time.
	 * 
	 * @return LIGHTWEIGHT, HEAVYWEIGHT or HEAVYWEIGHT_NEW.
	 */
	JobEntityClassification getClassification();

	/**
	 * Executes the current job at the given consensus timestamp.
	 * @param now the consensus timestamp
	 * @return true if successful, otherwise false
	 */
	boolean execute(long now);

	/**
	 * Useful for lightweight jobs, as their entities should be reviewed on startup. Example: transaction records.
	 */
	void reviewExistingEntities();
	
	EntityId getAffectedEntityId();
	
}
