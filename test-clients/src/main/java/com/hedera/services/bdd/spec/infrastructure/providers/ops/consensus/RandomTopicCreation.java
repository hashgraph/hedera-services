package com.hedera.services.bdd.spec.infrastructure.providers.ops.consensus;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.EntityNameProvider;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.names.RegistrySourcedNameProvider;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TopicID;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static com.hedera.services.bdd.spec.keys.KeyShape.listOf;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOPIC_EXPIRED;

public class RandomTopicCreation implements OpProvider {
        public static final int DEFAULT_CEILING_NUM = 100;

        private int ceilingNum = DEFAULT_CEILING_NUM;

        private final AtomicInteger opNo = new AtomicInteger();
        private final EntityNameProvider<Key> keys;
        private final RegistrySourcedNameProvider<TopicID> topics;
        private final ResponseCodeEnum[] permissibleOutcomes = standardOutcomesAnd(
                        INVALID_TOPIC_ID,
                        TOPIC_EXPIRED);

        public RandomTopicCreation(EntityNameProvider<Key> keys, RegistrySourcedNameProvider<TopicID> topics) {
                this.keys = keys;
                this.topics = topics;
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
                                .hasPrecheckFrom(STANDARD_PERMISSIBLE_PRECHECKS)
                                .hasAnyPrecheck()
                                .hasKnownStatusFrom(permissibleOutcomes);

                return Optional.of(op);
        }

        @Override
        public List<HapiSpecOperation> suggestedInitializers() {
                return List.of(newKeyNamed(my("simpleKey")));
        }

        private String my(String opName) {
                return unique(opName, RandomTopicCreation.class);
        }
}
