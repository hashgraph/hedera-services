/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.regression.system;

import static com.hedera.node.app.hapi.utils.CommonUtils.noThrowSha384HashOf;
import static com.hedera.services.bdd.junit.TestTags.UPGRADE;
import static com.hedera.services.bdd.junit.hedera.MarkerFile.EXEC_IMMEDIATE_MF;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getVersionInfo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.buildUpgradeZipWith;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freezeUpgrade;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.prepareUpgrade;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.restartNetworkFromUpgradeJar;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.updateSpecialFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateAddressBooks;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitForActiveNetwork;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitForMf;
import static com.hedera.services.bdd.spec.utilops.upgrade.BuildUpgradeZipOp.DEFAULT_UPGRADE_ZIP_LOC;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.transactions.TxnUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.stream.Stream;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(UPGRADE)
public class SameNodesUpgradeTest implements LifecycleTest {
    private static final Duration EXEC_IMMEDIATE_MF_TIMEOUT = Duration.ofSeconds(10);
    private static final SemanticVersion UPGRADE_VERSION =
            SemanticVersion.newBuilder().minor(99).build();

    @HapiTest
    final Stream<DynamicTest> upgradeSoftwareVersionWithSameAddressBook() {
        return hapiTest(
                // Build an upgrade.zip with the new version
                buildUpgradeZipWith(UPGRADE_VERSION),
                // Upload it to file 0.0.150; need sourcing() here because the operation reads contents eagerly
                sourcing(() -> updateSpecialFile(
                        GENESIS,
                        "0.0.150",
                        DEFAULT_UPGRADE_ZIP_LOC,
                        TxnUtils.BYTES_4K,
                        512)),
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
                restartNetworkFromUpgradeJar(),
                // Wait for all nodes to be ACTIVE
                waitForActiveNetwork(RESTART_TIMEOUT),
                // Confirm the version changed
                getVersionInfo().hasServicesVersion(UPGRADE_VERSION));
    }

    protected static byte[] upgradeFileHashAt(@NonNull final Path path) {
        return upgradeFileHashAt(path, false);
    }

    /**
     * Given a path, returns the SHA-384 hash of its contents. If the path is invalid or the file cannot be read,
     * an empty byte array is returned when {@code suppressExceptions=true} and an exception is propagated otherwise.
     *
     * <p>We support suppressing exceptions because that is the behavior in JRS now and out of an abundance of
     * caution we don't want to change it until those tests are replaced.
     *
     * @param path the path to the file
     * @param suppressExceptions whether to suppress exceptions
     * @return the SHA-384 hash of the file's contents or an empty byte array if the file cannot be read
     */
    protected static byte[] upgradeFileHashAt(@NonNull final Path path, final boolean suppressExceptions) {
        try {
            final var fileBytes = Files.readAllBytes(path);
            return noThrowSha384HashOf(fileBytes);
        } catch (final InvalidPathException | IOException e) {
            if (!suppressExceptions) {
                throw new RuntimeException(e);
            }
        }
        return new byte[0];
    }
}
