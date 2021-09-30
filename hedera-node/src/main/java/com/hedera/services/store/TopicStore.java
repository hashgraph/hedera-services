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

import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.records.TransactionRecordService;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Topic;
import com.hedera.services.utils.EntityNum;
import com.swirlds.merkle.map.MerkleMap;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.function.Supplier;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.store.models.TopicConversion.fromMerkle;
import static com.hedera.services.store.models.TopicConversion.fromModel;
import static com.hedera.services.store.models.TopicConversion.modelToMerkle;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_ID;

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
	 * @param topic
	 * 		- the topic to be mapped onto a new {@link MerkleTopic} and persisted.
	 */
	public void persistNew(Topic topic) {
		final var id = topic.getId().asEntityNum();
		final var currentTopics = topics.get();
		final var merkleTopic = fromModel(topic);
		merkleTopic.setSequenceNumber(0);
		currentTopics.put(id, merkleTopic);
		transactionRecordService.includeChangesToTopic(topic);
	}

	/**
	 * Returns a model of the requested topic, with operations that can be used to
	 * implement business logic in a transaction.
	 *
	 * <b>IMPORTANT:</b> Changes to the returned model are not automatically persisted
	 * to state! The altered model must be passed to {@link TopicStore#persistTopic(Topic)}
	 * in order for its changes to be applied to the Swirlds state, and included in the
	 * {@link com.hedera.services.state.submerkle.ExpirableTxnRecord} for the active transaction.
	 *
	 * @param id
	 * 		the topic to load
	 * @return a usable model of the topic
	 * @throws InvalidTransactionException
	 * 		if the requested topic is missing, deleted, or expired and pending removal
	 */
	public Topic loadTopic(Id id) {
		final var merkleTopic = topics.get().get(EntityNum.fromLong(id.getNum()));
		validateUsable(merkleTopic);
		return fromMerkle(merkleTopic, id);
	}

	/**
	 * Takes a Topic model and parses it to the corresponding MerkleTopic.
	 *
	 * @param topic
	 * 		the topic model
	 */
	public void persistTopic(Topic topic) {
		final var key = EntityNum.fromLong(topic.getId().getNum());
		var mutableTopic = topics.get().getForModify(key);
		modelToMerkle(topic, mutableTopic);
	}

	private void validateUsable(MerkleTopic merkleTopic) {
		validateFalse(merkleTopic == null || merkleTopic.isDeleted(), INVALID_TOPIC_ID);
	}
}
