/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.consensus;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNAUTHORIZED;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.merkle.map.MerkleMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class TopicDeleteTransitionLogic implements TransitionLogic {
    private static final Function<TransactionBody, ResponseCodeEnum> SEMANTIC_RUBBER_STAMP =
            ignore -> OK;

    private final Supplier<MerkleMap<EntityNum, MerkleTopic>> topics;
    private final OptionValidator validator;
    private final SigImpactHistorian sigImpactHistorian;
    private final TransactionContext transactionContext;

    @Inject
    public TopicDeleteTransitionLogic(
            final Supplier<MerkleMap<EntityNum, MerkleTopic>> topics,
            final OptionValidator validator,
            final SigImpactHistorian sigImpactHistorian,
            final TransactionContext transactionContext) {
        this.topics = topics;
        this.validator = validator;
        this.sigImpactHistorian = sigImpactHistorian;
        this.transactionContext = transactionContext;
    }

    @Override
    public void doStateTransition() {
        var op = transactionContext.accessor().getTxn().getConsensusDeleteTopic();
        var topicId = op.getTopicID();

        var topicStatus = validator.queryableTopicStatus(topicId, topics.get());
        if (OK != topicStatus) {
            // Should not get here as the adminKey lookup should have failed.
            transactionContext.setStatus(topicStatus);
            return;
        }

        var topicMapKey = EntityNum.fromTopicId(topicId);
        var topic = topics.get().get(topicMapKey);
        if (!topic.hasAdminKey()) {
            // Topics without adminKeys can't be deleted.
            transactionContext.setStatus(UNAUTHORIZED);
            return;
        }

        var mutableTopic = topics.get().getForModify(topicMapKey);
        mutableTopic.setDeleted(true);

        transactionContext.setStatus(SUCCESS);
        sigImpactHistorian.markEntityChanged(topicId.getTopicNum());
    }

    @Override
    public Predicate<TransactionBody> applicability() {
        return TransactionBody::hasConsensusDeleteTopic;
    }

    /** No transaction-specific pre-consensus checks. */
    @Override
    public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
        return SEMANTIC_RUBBER_STAMP;
    }
}
