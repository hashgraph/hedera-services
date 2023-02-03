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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOPIC_EXPIRED;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.EntityNameProvider;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TopicID;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class RandomTopicInfo implements OpProvider {
    private final ResponseCodeEnum[] permissibleCostAnswerPrechecks =
            standardQueryPrechecksAnd(TOPIC_EXPIRED, INVALID_TOPIC_ID);

    private final ResponseCodeEnum[] permissibleAnswerOnlyPrechecks =
            standardQueryPrechecksAnd(TOPIC_EXPIRED, INVALID_TOPIC_ID);

    private final EntityNameProvider<TopicID> topics;

    public RandomTopicInfo(EntityNameProvider<TopicID> topics) {
        this.topics = topics;
    }

    @Override
    public List<HapiSpecOperation> suggestedInitializers() {
        return Collections.emptyList();
    }

    @Override
    public Optional<HapiSpecOperation> get() {
        final var target = topics.getQualifying();
        if (target.isEmpty()) {
            return Optional.empty();
        }

        var op =
                QueryVerbs.getTopicInfo(target.get())
                        .hasCostAnswerPrecheckFrom(permissibleCostAnswerPrechecks)
                        .hasAnswerOnlyPrecheckFrom(permissibleAnswerOnlyPrechecks);

        return Optional.of(op);
    }
}
