package com.hedera.services.bdd.suites.jrs;

import com.hedera.services.bdd.suites.HapiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;

import java.util.List;
import java.util.stream.Stream;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nodeDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nodeUpdate;
import static com.hedera.services.bdd.suites.freeze.CommonUpgradeResources.initializeSettings;

public class UpdateNodeForUpgrade extends HapiSuite {
    private static final Logger log = LogManager.getLogger(UpdateNodeForUpgrade.class);

    public static void main(String... args) {
        new UpdateNodeForUpgrade().runSuiteSync();
    }

    @Override
    public List<Stream<DynamicTest>> getSpecsInSuite() {
        return List.of(updateNode());
    }

    final Stream<DynamicTest> updateNode() {
        return defaultHapiSpec("UpdateNodeForUpgrade")
                .given(initializeSettings())
                .when(nodeUpdate("0").accountId("0.0.30").signedBy(GENESIS))
                .then();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
