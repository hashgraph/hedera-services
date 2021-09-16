package com.hedera.services.store;

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

import com.hedera.services.records.TransactionRecordService;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.store.models.Topic;
import com.hedera.services.utils.EntityNum;
import com.swirlds.merkle.map.MerkleMap;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.function.Supplier;

/**
 * A store which interacts with the state topics, represented in a {@link MerkleMap}.
 * 
 * @author Yoan Sredkov
 */
@Singleton
public class TopicStore {
	
	private final Supplier<MerkleMap<EntityNum, MerkleTopic>> topics;
	private final TransactionRecordService transactionRecordService;

	@Inject
	public TopicStore(
			final Supplier<MerkleMap<EntityNum, MerkleTopic>> topics,
			final TransactionRecordService transactionRecordService
	) {
		this.topics = topics;
		this.transactionRecordService = transactionRecordService;
	}

	/**
	 * Persists a new {@link Topic} into the state, as well as exporting its ID to the transaction receipt.
	 *
	 * @param model
	 * 		- the model to be mapped onto a new {@link MerkleTopic} and persisted.
	 */
	public void persistNew(Topic model) {
		final var id = model.getId().asEntityNum();
		final var currentTopics = topics.get();
		final var merkleTopic = new MerkleTopic();
		mapModelToMerkle(model, merkleTopic);
		merkleTopic.setSequenceNumber(0);
		currentTopics.put(id, merkleTopic);
		transactionRecordService.includeChangesToTopic(model);
	}

	/**
	 * Maps properties between a model {@link Topic} and a {@link MerkleTopic}
	 * 
	 * @param model - the Topic model which will be used to map into a MerkleTopic
	 * @param merkle - the merkle topic
	 */
	private void mapModelToMerkle(Topic model, MerkleTopic merkle) {
		merkle.setAdminKey(model.getAdminKey());
		merkle.setSubmitKey(model.getSubmitKey());
		merkle.setMemo(model.getMemo());
		if (model.getAutoRenewAccountId() != null) {
			merkle.setAutoRenewAccountId(model.getAutoRenewAccountId().asEntityId());
		}
		merkle.setAutoRenewDurationSeconds(model.getAutoRenewDurationSeconds());
		merkle.setExpirationTimestamp(model.getExpirationTimestamp());
		merkle.setDeleted(model.isDeleted());
		merkle.setSequenceNumber(model.getSequenceNumber());
	}

}
