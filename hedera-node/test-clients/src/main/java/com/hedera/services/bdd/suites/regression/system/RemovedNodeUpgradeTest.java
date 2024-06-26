package com.hedera.services.bdd.suites.regression.system;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.hedera.NodeSelector;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

import java.time.Duration;
import java.util.stream.Stream;

import static com.hedera.services.bdd.junit.TestTags.UPGRADE;
import static com.hedera.services.bdd.junit.hedera.MarkerFile.EXEC_IMMEDIATE_MF;
import static com.hedera.services.bdd.junit.hedera.NodeSelector.byNodeId;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getVersionInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.buildUpgradeZipWith;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freezeUpgrade;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingThree;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingTwo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.prepareUpgrade;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.removeNode;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.restartNetworkFromUpgradeJar;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.updateSpecialFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitForActiveNetwork;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitForMf;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitUntilStartOfNextStakingPeriod;
import static com.hedera.services.bdd.spec.utilops.upgrade.BuildUpgradeZipOp.DEFAULT_UPGRADE_ZIP_LOC;
import static com.hedera.services.bdd.suites.HapiSuite.FUNDING;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_MILLION_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.STAKING_REWARD;
import static com.hedera.services.bdd.suites.regression.system.SameNodesUpgradeTest.upgradeFileHashAt;

@Tag(UPGRADE)
public class RemovedNodeUpgradeTest implements LifecycleTest {
    private static final Duration EXEC_IMMEDIATE_MF_TIMEOUT = Duration.ofSeconds(10);
    private static final SemanticVersion UPGRADE_VERSION =
            SemanticVersion.newBuilder().minor(99).build();

    @HapiTest
    final Stream<DynamicTest> upgradeSoftwareVersionAfterNodeRemoval() {
        return hapiTest(
                overridingTwo(
                        "staking.startThreshold", "" + 10 * ONE_HBAR,
                        "staking.rewardBalanceThreshold", "" + 0),
                cryptoTransfer(tinyBarsFromTo(GENESIS, STAKING_REWARD, ONE_MILLION_HBARS)),
                cryptoCreate("node0Staker")
                        .balance(1_000 * ONE_MILLION_HBARS)
                        .stakedNodeId(0),
                cryptoCreate("node1Staker")
                        .balance(1_000 * ONE_MILLION_HBARS)
                        .stakedNodeId(1),
                cryptoCreate("node2Staker")
                        .balance(1_000 * ONE_MILLION_HBARS)
                        .stakedNodeId(2),
                cryptoCreate("node3Staker")
                        .balance(ONE_MILLION_HBARS)
                        .stakedNodeId(3),
                waitUntilStartOfNextStakingPeriod(1),
                // Build an upgrade.zip with the new version
                buildUpgradeZipWith(UPGRADE_VERSION),
                // Upload it to file 0.0.150; need sourcing() here because the operation reads contents eagerly
                sourcing(() -> updateSpecialFile(
                        GENESIS,
                        "0.0.150",
                        DEFAULT_UPGRADE_ZIP_LOC,
                        TxnUtils.BYTES_4K,
                        512)),
                waitUntilStartOfNextStakingPeriod(1),
                cryptoTransfer(TokenMovement.movingHbar(4).distributing(GENESIS,
                        "node0Staker","node1Staker","node2Staker","node3Staker")).via("firstCollection"),
                getTxnRecord("firstCollection").logged().hasPaidStakingRewardsCount(4),
                // Issue PREPARE_UPGRADE; need sourcing() here because we want to hash only after creating the ZIP
                sourcing(() -> prepareUpgrade()
                        .withUpdateFile("0.0.150")
                        .havingHash(upgradeFileHashAt(DEFAULT_UPGRADE_ZIP_LOC))),
                // Wait for the immediate execution marker file (written only after 0.0.150 is unzipped)
                waitForMf(EXEC_IMMEDIATE_MF, EXEC_IMMEDIATE_MF_TIMEOUT),
                // (DAB ONLY) Validate all four nodes are written
//                validateAddressBooks(
//                        addressBook -> assertEquals(4, addressBook.getSize(), "Wrong number of nodes in address book")),
                // Issue FREEZE_UPGRADE; need sourcing() here because we want to hash only after creating the ZIP
                sourcing(() -> freezeUpgrade()
                        .startingIn(2)
                        .seconds()
                        .withUpdateFile("0.0.150")
                        .havingHash(upgradeFileHashAt(DEFAULT_UPGRADE_ZIP_LOC))),
                confirmFreezeAndShutdown(),
                removeNode(byNodeId(2)),
                restartNetworkFromUpgradeJar(),
                // Wait for all nodes to be ACTIVE
                waitForActiveNetwork(RESTART_TIMEOUT),
                cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1)),
                // Confirm the version changed
                getVersionInfo().hasServicesVersion(UPGRADE_VERSION),
                waitUntilStartOfNextStakingPeriod(1),
                cryptoTransfer(TokenMovement.movingHbar(4).distributing(GENESIS,
                        "node0Staker","node1Staker","node2Staker","node3Staker")).via("secondCollection"),
                getTxnRecord("secondCollection").logged().hasPaidStakingRewardsCount(3));
    }
}
