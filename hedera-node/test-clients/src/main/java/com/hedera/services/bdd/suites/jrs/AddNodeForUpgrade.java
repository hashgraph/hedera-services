package com.hedera.services.bdd.suites.jrs;

import com.hedera.services.bdd.suites.HapiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;

import java.util.List;
import java.util.stream.Stream;

import static com.hedera.services.bdd.spec.HapiPropertySource.asAccount;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nodeCreate;
import static com.hedera.services.bdd.suites.crypto.CryptoCreateSuite.ED_25519_KEY;
import static com.hedera.services.bdd.suites.freeze.CommonUpgradeResources.initializeSettings;
import static com.hedera.services.bdd.suites.hip869.NodeCreateTest.GOSSIP_ENDPOINTS_IPS;
import static com.hedera.services.bdd.suites.hip869.NodeCreateTest.SERVICES_ENDPOINTS_IPS;
import static com.hedera.services.bdd.suites.hip869.NodeCreateTest.generateX509Certificates;

public class AddNodeForUpgrade extends HapiSuite {
    private static final Logger log = LogManager.getLogger(AddNodeForUpgrade.class);

    public static void main(String... args) {
        new AddNodeForUpgrade().runSuiteSync();
    }

    @Override
    public List<Stream<DynamicTest>> getSpecsInSuite() {
        return List.of(createNode());
    }

    final Stream<DynamicTest> createNode() {
        final var gossipCertificates = generateX509Certificates(2);
        byte[] hash = new byte[0];
        try {
            hash = gossipCertificates.getFirst().getEncoded();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return defaultHapiSpec("AddNodeForUpgrade")
                .given(initializeSettings())
                .when(nodeCreate("node")
                        .signedBy(GENESIS)
                        .accountId(asAccount("0.0.300"))
                        .description("Node300AddedForUpgrade")
                        .gossipEndpoint(GOSSIP_ENDPOINTS_IPS)
                        .serviceEndpoint(SERVICES_ENDPOINTS_IPS)
                        .gossipCaCertificate(hash)
                        .grpcCertificateHash("hash".getBytes())
                        .adminKey(ED_25519_KEY)
                        .advertisingCreation())
                .then();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
