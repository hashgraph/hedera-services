package com.hedera.services.bdd.spec.infrastructure.providers.ops.consensus;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.EntityNameProvider;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.transactions.consensus.HapiMessageSubmit;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TopicID;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOPIC_EXPIRED;
import static java.util.Collections.EMPTY_LIST;

public class RandomMessageSubmit implements OpProvider {
        private static final Logger log = LogManager.getLogger(RandomMessageSubmit.class);
        private final ResponseCodeEnum[] permissibleOutcomes = standardOutcomesAnd(
                        TOPIC_EXPIRED,
                        INVALID_TOPIC_ID
        );

        private final SplittableRandom r = new SplittableRandom();
        private final EntityNameProvider<TopicID> topics;

        public RandomMessageSubmit(EntityNameProvider<TopicID> topics) {
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

                HapiMessageSubmit op = submitMessageTo(target.get())
                                .message("Hello Hedera")
                                .hasKnownStatusFrom(permissibleOutcomes);

                return Optional.of(op);
        }

}
