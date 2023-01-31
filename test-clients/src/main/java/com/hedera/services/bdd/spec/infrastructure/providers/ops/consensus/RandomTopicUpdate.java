/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.spec.infrastructure.providers.ops.consensus;

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.updateTopic;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOPIC_EXPIRED;
import static java.util.Collections.EMPTY_LIST;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.EntityNameProvider;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.transactions.consensus.HapiTopicUpdate;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TopicID;
import java.util.List;
import java.util.Optional;

public class RandomTopicUpdate implements OpProvider {
    private final EntityNameProvider<TopicID> topics;

    private final ResponseCodeEnum[] permissibleOutcomes =
            standardOutcomesAnd(TOPIC_EXPIRED, INVALID_TOPIC_ID);

    public RandomTopicUpdate(EntityNameProvider<TopicID> topics) {
        this.topics = topics;
    }

    @Override
    public List<HapiSpecOperation> suggestedInitializers() {
        return EMPTY_LIST;
    }

    @Override
    public Optional<HapiSpecOperation> get() {
        final var target = topics.getQualifying();
        if (target.isEmpty()) {
            return Optional.empty();
        }

        HapiTopicUpdate op =
                updateTopic(target.get())
                        .hasKnownStatusFrom(permissibleOutcomes)
                        .hasPrecheckFrom(STANDARD_PERMISSIBLE_PRECHECKS);
        return Optional.of(op);
    }
}
