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

package com.hedera.services.store.models;

import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.state.merkle.MerkleTopic;

import java.util.ArrayList;

/**
 * A utility class responsible for the mapping between a {@link Topic} and {@link MerkleTopic} ( and vice-versa ).
 *
 * @author Yoan Sredkov
 */
public class TopicConversion {

	private TopicConversion() {
		throw new UnsupportedOperationException("Utility class.");
	}

	public static Topic fromMerkle(final MerkleTopic merkleTopic, final Id id) {
		final var modelTopic = new Topic(id);
		merkleToModel(merkleTopic, modelTopic);
		return modelTopic;
	}

	/**
	 * Maps the model topic's fields with the fields of the Merkle topic
	 *
	 * @param model       The source model topic
	 * @param merkleTopic The targeted Merkle topic
	 */
	public static void mapModelChangesToMerkle(final Topic model, final MerkleTopic merkleTopic) {
		modelToMerkle(model, merkleTopic);
	}

	private static void merkleToModel(final MerkleTopic merkle, final Topic model) {
		if (JKeyList.equalUpToDecodability(merkle.getAdminKey(), new JKeyList(new ArrayList<>()))) {
			model.setAdminKey(null);
		} else {
			model.setAdminKey(merkle.getAdminKey());
		}
		if (merkle.hasSubmitKey()) {
			model.setSubmitKey(merkle.getSubmitKey());
		}
		if (merkle.hasAutoRenewAccountId()) {
			model.setAutoRenewAccountId(merkle.getAutoRenewAccountId().asId());
		}
		model.setMemo(merkle.getMemo());
		model.setAutoRenewDurationSeconds(merkle.getAutoRenewDurationSeconds());
		model.setExpirationTimestamp(merkle.getExpirationTimestamp());
		model.setDeleted(merkle.isDeleted());
		model.setSequenceNumber(merkle.getSequenceNumber());
	}

	public static MerkleTopic fromModel(final Topic model) {
		MerkleTopic merkle = new MerkleTopic();
		modelToMerkle(model, merkle);
		return merkle;
	}

	/**
	 * Maps properties between a model {@link Topic} and a {@link MerkleTopic}
	 *
	 * @param model  the Topic model which will be used to map into a MerkleTopic
	 * @param merkle the merkle topic
	 */
	private static void modelToMerkle(final Topic model, final MerkleTopic merkle) {
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
