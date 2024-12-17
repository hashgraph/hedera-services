/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOPIC_EXPIRED;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.SpecOperation;
import com.hedera.services.bdd.spec.infrastructure.EntityNameProvider;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.names.RegistrySourcedNameProvider;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TopicID;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class RandomTopicCreation implements OpProvider {
    public static final int DEFAULT_CEILING_NUM = 100;

    private int ceilingNum = DEFAULT_CEILING_NUM;

    private final AtomicInteger opNo = new AtomicInteger();
    private final EntityNameProvider keys;
    private final RegistrySourcedNameProvider<TopicID> topics;
    private final ResponseCodeEnum[] customOutcomes;
    private final ResponseCodeEnum[] permissibleOutcomes = standardOutcomesAnd(INVALID_TOPIC_ID, TOPIC_EXPIRED);

    public RandomTopicCreation(
            EntityNameProvider keys, RegistrySourcedNameProvider<TopicID> topics, ResponseCodeEnum[] customOutcomes) {
        this.keys = keys;
        this.topics = topics;
        this.customOutcomes = customOutcomes;
    }

    public RandomTopicCreation ceiling(int n) {
        ceilingNum = n;
        return this;
    }

    @Override
    public Optional<HapiSpecOperation> get() {
        if (topics.numPresent() >= ceilingNum) {
            return Optional.empty();
        }

        Optional<String> key = keys.getQualifying();
        if (key.isEmpty()) {
            return Optional.empty();
        }

        int n = opNo.getAndIncrement();
        final String newTopic = my("topic" + n);
        var op = createTopic(newTopic)
                .adminKeyName(key.get())
                .submitKeyName(key.get())
                .hasPrecheckFrom(plus(STANDARD_PERMISSIBLE_PRECHECKS, customOutcomes))
                .hasKnownStatusFrom(plus(permissibleOutcomes, customOutcomes));

        return Optional.of(op);
    }

    @Override
    public List<SpecOperation> suggestedInitializers() {
        return List.of(newKeyNamed(my("simpleKey")));
    }

    private String my(String opName) {
        return unique(opName, RandomTopicCreation.class);
    }
}
