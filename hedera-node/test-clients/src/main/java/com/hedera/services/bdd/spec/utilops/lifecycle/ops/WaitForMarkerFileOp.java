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

package com.hedera.services.bdd.spec.utilops.lifecycle.ops;

import static com.hedera.services.bdd.junit.hedera.ExternalPath.UPGRADE_ARTIFACTS_DIR;
import static com.hedera.services.bdd.junit.hedera.MarkerFile.EXEC_IMMEDIATE_MF;
import static com.hedera.services.bdd.junit.hedera.utils.WorkingDirUtils.CONFIG_TXT;
import static com.hedera.services.bdd.suites.freeze.CommonUpgradeResources.FAKE_UPGRADE_FILE_NAME;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.services.bdd.junit.hedera.HederaNode;
import com.hedera.services.bdd.junit.hedera.MarkerFile;
import com.hedera.services.bdd.junit.hedera.NodeSelector;
import com.hedera.services.bdd.spec.utilops.lifecycle.AbstractLifecycleOp;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Waits for the selected node or nodes specified by the {@link NodeSelector} to
 * have written the specified marker file within the given timeout.
 */
public class WaitForMarkerFileOp extends AbstractLifecycleOp {
    private static final Logger log = LogManager.getLogger(WaitForMarkerFileOp.class);

    private final Duration timeout;
    private final MarkerFile markerFile;

    public WaitForMarkerFileOp(
            @NonNull NodeSelector selector, @NonNull final MarkerFile markerFile, @NonNull final Duration timeout) {
        super(selector);
        this.timeout = requireNonNull(timeout);
        this.markerFile = requireNonNull(markerFile);
    }

    @Override
    protected void run(@NonNull final HederaNode node) {
        log.info(
                "Waiting for node '{}' to write marker file '{}' within {}",
                node.getName(),
                markerFile.fileName(),
                timeout);
        node.mfFuture(markerFile).orTimeout(timeout.toMillis(), MILLISECONDS).join();
        log.info("Node '{}' wrote marker file '{}'", node.getName(), markerFile.fileName());
        if (markerFile == EXEC_IMMEDIATE_MF) {
            // Since the node writes the marker file last, the fake upgrade file and config.txt should be present
            final var fakeUpgradeFile = node.getExternalPath(UPGRADE_ARTIFACTS_DIR)
                    .resolve(FAKE_UPGRADE_FILE_NAME)
                    .toFile();
            assertTrue(
                    fakeUpgradeFile.exists(),
                    "Node '" + node.getName() + "' did not extract ZIP during PREPARE_UPGRADE, missing "
                            + fakeUpgradeFile.getAbsolutePath());
            final var configTxt = node.getExternalPath(UPGRADE_ARTIFACTS_DIR)
                    .resolve(CONFIG_TXT)
                    .toFile();
            assertTrue(
                    configTxt.exists(),
                    "Node '" + node.getName() + "' did not write new config.txt, missing "
                            + configTxt.getAbsolutePath());
        }
    }
}
