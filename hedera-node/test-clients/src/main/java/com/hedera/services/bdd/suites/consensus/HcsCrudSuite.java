package com.hedera.services.bdd.suites.consensus;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asTopicId;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.deleteTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.updateTopic;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freezeOnly;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_ID;

public class HcsCrudSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(HcsCrudSuite.class);

    public static void main(String... args) {
        new HcsCrudSuite().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(basicCrud());
    }

    private HapiSpec basicCrud() {
        final var oldMemo = "old";
        final var newMemo = "new";
        final var firstKey = "firstKey";
        final var secondKey = "secondKey";
        final var topicToUpdate = "topicToUpdate";
        final var topicToDelete = "topicToDelete";

        return defaultHapiSpec("BasicCrud")
                .given(
                        newKeyNamed(firstKey),
                        newKeyNamed(secondKey),
                        // Create two topics, send some messages to both, delete one
                        createTopic(topicToUpdate)
                                .memo(oldMemo)
                                .adminKeyName(firstKey)
                                .submitKeyName(firstKey),
                        createTopic(topicToDelete)
                                .adminKeyName(firstKey))
                .when(
                        submitMessageTo(topicToUpdate)
                                .message("Hello"),
                        submitMessageTo(topicToDelete)
                                .message("World"),
                        submitMessageTo(topicToUpdate)
                                .message("!"))
                .then(
                        updateTopic(topicToUpdate)
                                .submitKey(secondKey)
                                .topicMemo(newMemo),
                        deleteTopic(topicToDelete),
                        // Trigger snapshot of final state for replay assets
                        freezeOnly().payingWith(GENESIS).startingAt(Instant.now().plusSeconds(10)));
    }
    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
