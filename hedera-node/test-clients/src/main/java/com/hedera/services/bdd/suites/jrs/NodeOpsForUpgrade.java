package com.hedera.services.bdd.suites.jrs;

import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.ServiceEndpoint;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static com.hedera.node.app.hapi.utils.CommonPbjConverters.toPbj;
import static com.hedera.services.bdd.junit.hedera.utils.AddressBookUtils.endpointFor;
import static com.hedera.services.bdd.spec.HapiPropertySource.asAccount;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nodeCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nodeDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nodeUpdate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.suites.crypto.CryptoCreateSuite.ED_25519_KEY;
import static com.hedera.services.bdd.suites.freeze.CommonUpgradeResources.initializeSettings;
import static com.hedera.services.bdd.suites.hip869.NodeCreateTest.GOSSIP_ENDPOINTS_IPS;
import static com.hedera.services.bdd.suites.hip869.NodeCreateTest.SERVICES_ENDPOINTS_IPS;
import static com.hedera.services.bdd.suites.hip869.NodeCreateTest.generateX509Certificates;

public class NodeOpsForUpgrade extends HapiSuite {
    private static final Logger log = LogManager.getLogger(NodeOpsForUpgrade.class);
    public static List<ServiceEndpoint> UPDATE_GOSSIP_ENDPOINTS_IPS =
            Arrays.asList(endpointFor("192.168.1.202", 123), endpointFor("192.168.1.203", 123));


    public static void main(String... args) {
        new NodeOpsForUpgrade().runSuiteSync();
    }

    @Override
    public List<Stream<DynamicTest>> getSpecsInSuite() {
        return List.of(doDelete());
    }

    final Stream<DynamicTest> doDelete() {
        final var gossipCertificates = generateX509Certificates(2);
        byte[] hash = new byte[0];
        try {
            hash = gossipCertificates.getFirst().getEncoded();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return defaultHapiSpec("NodeOpsForUpgrade")
                .given(initializeSettings())
                .when(overriding("nodes.enableDAB", "true"),
                        newKeyNamed(ED_25519_KEY).shape(KeyShape.ED25519),
                        nodeDelete("2").payingWith(GENESIS).signedBy(GENESIS),
                        nodeUpdate("0")
                                .description("UpdatedNode0")
                                .gossipEndpoint(toPbj(UPDATE_GOSSIP_ENDPOINTS_IPS))
                                .payingWith(GENESIS)
                                .signedBy(GENESIS),
                        nodeCreate("node5")
                                .signedBy(GENESIS)
                                .accountId(asAccount("0.0.300"))
                                .description("Node5AddedForUpgrade")
                                .gossipEndpoint(GOSSIP_ENDPOINTS_IPS)
                                .serviceEndpoint(SERVICES_ENDPOINTS_IPS)
                                .gossipCaCertificate(hash)
                                .grpcCertificateHash("hash".getBytes())
                                .adminKey(ED_25519_KEY)
                                .advertisingCreation()
                                .payingWith(GENESIS)
                                .signedBy(GENESIS, ED_25519_KEY))
                .then();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
