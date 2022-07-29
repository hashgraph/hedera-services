/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.store.models;

import com.hedera.services.state.merkle.MerkleTopic;

/**
 * A utility class responsible for the mapping between a {@link Topic} and {@link MerkleTopic} ( and
 * vice-versa ).
 *
 * @author Yoan Sredkov
 */
public final class TopicConversion {

    private TopicConversion() {
        throw new UnsupportedOperationException("Utility class.");
    }

    public static Topic fromMerkle(final MerkleTopic merkleTopic, final Id id) {
        final var modelTopic = new Topic(id);
        merkleToModel(merkleTopic, modelTopic);
        return modelTopic;
    }

    private static void merkleToModel(final MerkleTopic merkle, final Topic model) {
        model.setAdminKey(merkle.getAdminKey());
        model.setSubmitKey(merkle.getSubmitKey());
        model.setMemo(merkle.getMemo());
        if (merkle.getAutoRenewAccountId() != null) {
            model.setAutoRenewAccountId(merkle.getAutoRenewAccountId().asId());
        }
        model.setAutoRenewDurationSeconds(merkle.getAutoRenewDurationSeconds());
        model.setExpirationTimestamp(merkle.getExpirationTimestamp());
        model.setDeleted(merkle.isDeleted());
        model.setSequenceNumber(merkle.getSequenceNumber());
    }

    public static MerkleTopic fromModel(final Topic model) {
        final var merkle = new MerkleTopic();
        modelToMerkle(model, merkle);
        return merkle;
    }

    /**
     * Maps properties between a model {@link Topic} and a {@link MerkleTopic}
     *
     * @param model - the Topic model which will be used to map into a MerkleTopic
     * @param merkle - the merkle topic
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
