/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.yahcli.suites;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiSuite;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;

public class UpgradeHelperSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(UpgradeHelperSuite.class);

    private final byte[] upgradeFileHash;
    private final String upgradeFile;
    /* Null for a PREPARE_UPGRADE, non-null for a TELEMETRY_UPGRADE or FREEZE_UPGRADE */
    private final Instant startTime;
    private final Map<String, String> specConfig;
    private final boolean isTelemetryUpgrade;

    public UpgradeHelperSuite(
            final Map<String, String> specConfig, final byte[] upgradeFileHash, final String upgradeFile) {
        this(specConfig, upgradeFileHash, upgradeFile, null, false);
    }

    public UpgradeHelperSuite(
            final Map<String, String> specConfig,
            final byte[] upgradeFileHash,
            final String upgradeFile,
            @Nullable final Instant startTime) {
        this(specConfig, upgradeFileHash, upgradeFile, startTime, false);
    }

    public UpgradeHelperSuite(
            final Map<String, String> specConfig,
            final byte[] upgradeFileHash,
            final String upgradeFile,
            @Nullable final Instant startTime,
            final boolean isTelemetryUpgrade) {
        this.specConfig = specConfig;
        this.upgradeFile = upgradeFile;
        this.upgradeFileHash = upgradeFileHash;
        this.startTime = startTime;
        this.isTelemetryUpgrade = isTelemetryUpgrade;
    }

    @Override
    public List<Stream<DynamicTest>> getSpecsInSuite() {
        return List.of(doStagingAction());
    }

    final Stream<DynamicTest> doStagingAction() {
        final HapiSpecOperation op;

        if (startTime == null) {
            op = UtilVerbs.prepareUpgrade()
                    .noLogging()
                    .withUpdateFile(upgradeFile)
                    .havingHash(upgradeFileHash);
        } else if (isTelemetryUpgrade) {
            op = UtilVerbs.telemetryUpgrade()
                    .noLogging()
                    .startingAt(startTime)
                    .withUpdateFile(upgradeFile)
                    .havingHash(upgradeFileHash);
        } else {
            op = UtilVerbs.freezeUpgrade()
                    .noLogging()
                    .startingAt(startTime)
                    .withUpdateFile(upgradeFile)
                    .havingHash(upgradeFileHash);
        }

        return HapiSpec.customHapiSpec("DoStagingAction")
                .withProperties(specConfig)
                .given()
                .when()
                .then(op);
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
