package com.hedera.services.bdd.spec.infrastructure.providers.ops.consensus;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.EntityNameProvider;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.crypto.RandomTransfer;
import com.hedera.services.bdd.spec.transactions.consensus.HapiMessageSubmit;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TopicID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.SynchronousQueue;
import java.util.stream.IntStream;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static java.util.Collections.EMPTY_LIST;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

public class RandomMessageSubmit implements OpProvider {
        private static final Logger log = LogManager.getLogger(RandomMessageSubmit.class);

        public static final int DEFAULT_NUM_STABLE_TOPICS = 500;
        private final ResponseCodeEnum[] permissibleOutcomes = standardOutcomesAnd(
                TOPIC_EXPIRED,
                INVALID_TOPIC_ID,
                INVALID_CHUNK_NUMBER,
                INVALID_CHUNK_TRANSACTION_ID
        );

        private final SplittableRandom r = new SplittableRandom();
        private final EntityNameProvider<TopicID> topics;
        private int numStableTopics = DEFAULT_NUM_STABLE_TOPICS;
        private static byte[] messageBytes = new byte[2048];
        static {
                Arrays.fill(messageBytes, (byte) 0b1);
        }

        public RandomMessageSubmit(EntityNameProvider<TopicID> topics) {
                this.topics = topics;
        }

        public RandomMessageSubmit numStableTopics(int n) {
                numStableTopics = n;
                return this;
        }

        public static Set<String> stableTopics(int n) {
                return IntStream.range(0, n)
                        .mapToObj(i -> String.format("stable-topic%d", i)).collect(toSet());
        }

        @Override
        public List<HapiSpecOperation> suggestedInitializers() {
                return stableTopics(numStableTopics).stream()
                        .map(topic -> createTopic(my(topic)).noLogging().deferStatusResolution())
                        .collect(toList());
        }

        private String my(String opName) {
                return unique(opName, RandomMessageSubmit.class);
        }

        @Override
        public Optional<HapiSpecOperation> get() {
                final var target = topics.getQualifying();
                if (target.isEmpty()) {
                        return Optional.empty();
                }

                HapiMessageSubmit op = submitMessageTo(target.get())
                        .message(new String(messageBytes))
//                        .chunkInfo(r.nextInt(10) + 1, r.nextInt(3) + 1)
                        .hasKnownStatusFrom(permissibleOutcomes)
                        .hasPrecheckFrom(STANDARD_PERMISSIBLE_PRECHECKS)
                        .hasAnyPrecheck();

                if (r.nextBoolean()) {
                        op = op.usePresetTimestamp();
                }

                return Optional.of(op);
        }
}
