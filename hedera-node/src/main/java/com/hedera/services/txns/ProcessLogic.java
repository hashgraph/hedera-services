package com.hedera.services.txns;

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

import com.swirlds.common.Transaction;
import java.time.Instant;

/**
 * Defines a type that can delegate to the correct state transition, if any,
 * implied by the given transaction and consensus time in the active
 * node context.
 *
 * Note that any transaction-specific state transition can only be correct
 * if signing, fee, and other generic prerequisites are met. Hence the main
 * responsibility of the process logic is to validate these prerequisites,
 * and then delegate to the correct {@link TransitionLogic} implementation.
 *
 * @author Michael Tinker
 */
public interface ProcessLogic {
	/**
	 * Orchestrates a process to express the full implications of the given
	 * consensus transaction at the specified time.
	 *
	 * @param platformTxn the consensus transaction to incorporate.
	 * @param consensusTime the authoritative time of consensus.
	 * @param submittingMember the id of the member that submitted the txn
	 */
	void incorporateConsensusTxn(Transaction platformTxn, Instant consensusTime, long submittingMember);
}
