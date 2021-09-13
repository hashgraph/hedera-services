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
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.store.models.Topic;
import com.swirlds.fcmap.FCMap;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.function.Supplier;

@Singleton
public class TopicStore {

	private final Supplier<FCMap<MerkleEntityId, MerkleTopic>> topics;
	private final TransactionRecordService transactionRecordService;

	@Inject
	public TopicStore(final Supplier<FCMap<MerkleEntityId, MerkleTopic>> topics, final TransactionRecordService transactionRecordService) {
		this.topics = topics;
		this.transactionRecordService = transactionRecordService;
	}

	/**
	 * Persists a new {@link Topic} into the state.
	 *
	 * @param model
	 * 		- the model to be mapped onto a **new** {@link MerkleTopic} and persisted.
	 */
	public void persistNew(Topic model) {
		final var id = model.getId().asMerkle();
		final var currentTopics = topics.get();
		final var merkleTopic = new MerkleTopic();
		mapModelToMerkle(model, merkleTopic);
		merkleTopic.setSequenceNumber(0);
		currentTopics.put(id, merkleTopic);
		transactionRecordService.includeNewTopic(model.getId().asGrpcTopic());
	}

	public void mapModelToMerkle(Topic model, MerkleTopic merkle) {
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
