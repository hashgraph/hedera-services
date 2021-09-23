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
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Topic;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.swirlds.merkle.map.MerkleMap;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.function.Supplier;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.store.models.TopicConversion.fromMerkle;
import static com.hedera.services.store.models.TopicConversion.fromModel;
import static com.hedera.services.store.models.TopicConversion.modelToMerkle;

import static com.hedera.services.store.models.TopicConversion.fromModel;

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
	 * Persists the changes made to a {@link Topic} model into the state,
	 * by mapping those changes onto a {@link MerkleTopic}.
	 *
	 * @param topic
	 * 		- the model to be persisted
	 */
	public void persistTopic(Topic topic) {
		final var id = topic.getId().asEntityNum();
		final var mutableMerkleTopic = topics.get().getForModify(id);
		
		modelToMerkle(topic, mutableMerkleTopic);
		transactionRecordService.includeChangesToTopic(topic);
	}

	/**
	 * Performs loading of a given merkle topic from state. The merkle topic is mapped to a model.
	 * Please note that the model should be explicitly persisted with {@link TopicStore#persistTopic}
	 * in order to apply the changes made.
	 *
	 * @param id
	 * 		- model ID of the topic to be loaded.
	 * @return {@link Topic} - the loaded topic
	 */
	public Topic loadTopic(Id id) {
		final var entityNum = id.asEntityNum();
		final var currentTopics = topics.get();
		final var merkleTopic = currentTopics.get(entityNum);

		validateUsable(merkleTopic);
		return fromMerkle(merkleTopic, id);
	}

	/**
	 * Validates whether the given {@link MerkleTopic} is usable for operations.
	 * 
	 * @param topic - the topic to be validated
	 */
	private void validateUsable(MerkleTopic topic) {
		validateFalse(topic == null, ResponseCodeEnum.INVALID_TOPIC_ID);
		validateFalse(topic.isDeleted(), ResponseCodeEnum.INVALID_TOPIC_ID);
	}

}
