package com.hedera.services.ledger.ids;

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

import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import com.swirlds.common.SwirldDualState;
import com.swirlds.common.SwirldTransaction;

import java.time.Instant;

/**
 * Defines a type able to create ids of various entities under various conditions.
 */
public interface EntityIdSource {

	/**
	 * Returns the next {@link TopicID} to use.
	 * 
	 * @return the next topic id
	 */
	TopicID newTopicId();
	
	/**
	 * Returns the next account number.
	 *
	 * @return the next account number
	 */
	EntityNum newAccountId();

	/**
	 * Returns the next {@link ContractID} to use.
	 *
	 * @return the next contract id
	 */
	ContractID newContractId();

	/**
	 * Returns the next {@link FileID} to use.
	 *
	 * @return the next file id
	 */
	FileID newFileId();

	/**
	 * Returns the next {@link TokenID} to use.
	 *
	 * @return the next token id
	 */
	TokenID newTokenId();

	/**
	 * Returns the next {@link ScheduleID} to use.
	 *
	 * @return the next schedule id
	 */
	ScheduleID newScheduleId();

	/**
	 * Reclaims the last id issued.
	 */
	void reclaimLastId();

	/**
	 * Reclaims the IDs issued during one
	 * {@link com.hedera.services.ServicesState#handleTransaction(long, boolean, Instant, Instant, SwirldTransaction, SwirldDualState)} transition
	 */
	void reclaimProvisionalIds();

	/**
	 * Resets the provisional ids created during one
	 * {@link com.hedera.services.ServicesState#handleTransaction(long, boolean, Instant, Instant, SwirldTransaction, SwirldDualState)} transition
	 */
	void resetProvisionalIds();
}
