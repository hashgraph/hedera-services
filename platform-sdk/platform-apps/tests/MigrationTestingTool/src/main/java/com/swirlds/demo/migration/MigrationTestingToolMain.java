/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.demo.migration;

import static com.swirlds.common.utility.Units.NANOSECONDS_TO_SECONDS;

import com.swirlds.common.system.BasicSoftwareVersion;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.PlatformWithDeprecatedMethods;
import com.swirlds.common.system.SwirldMain;
import com.swirlds.common.system.SwirldState;
import com.swirlds.fcqueue.FCQueueStatistics;
import com.swirlds.logging.payloads.ApplicationFinishedPayload;
import com.swirlds.merkle.map.MerkleMapMetrics;
import com.swirlds.platform.gui.SwirldsGui;
import java.security.SignatureException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

/**
 * An application designed for testing migration from version to version.
 *
 * Command line arguments: Seed(long), TransactionsPerNode(int)
 */
public class MigrationTestingToolMain implements SwirldMain {

    private static final Logger logger = LogManager.getLogger(MigrationTestingToolMain.class);

    static final Marker MARKER = MarkerManager.getMarker("MIGRATION");

    private long seed;
    private int maximumTransactionsPerNode;
    private int transactionsCreated;
    private TransactionGenerator generator;
    private Platform platform;

    /** create at most this many transactions in preEvent, even if more is needed to meet target rate */
    private final int transPerEventMax = 2048;
    /** transactions in each Event */
    private final int transPerSecToCreate = 100;

    private double toCreate = 0;
    private long lastEventTime = System.nanoTime();

    private static final BasicSoftwareVersion softwareVersion = new BasicSoftwareVersion(1);

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(final Platform platform, final NodeId selfId) {
        this.platform = platform;

        final String[] parameters = ((PlatformWithDeprecatedMethods) platform).getParameters();
        logger.info(MARKER, "Parsing arguments {}", (Object) parameters);
        seed = Long.parseLong(parameters[0]) + selfId.getId();
        maximumTransactionsPerNode = Integer.parseInt(parameters[1]);

        generator = new TransactionGenerator(seed);
        SwirldsGui.setAbout(platform.getSelfId().getId(), "MigrationTestingApp");

        // Initialize application statistics
        initAppStats();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run() {
        try {
            logger.info(
                    MARKER,
                    "MigrationTestingApp started handling {} transactions with seed {}",
                    maximumTransactionsPerNode,
                    seed);

            final boolean isZeroStake = platform.getSelfAddress().isZeroStake();
            if (!isZeroStake) {
                while (transactionsCreated < maximumTransactionsPerNode) {
                    try {
                        generateEvents();
                        Thread.sleep(1_000);
                    } catch (final InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }

            logger.info(
                    MARKER, () -> new ApplicationFinishedPayload("MigrationTestingApp finished handling transactions"));
        } catch (final Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private void initAppStats() {
        // Register Platform data structure statistics
        FCQueueStatistics.register(platform.getContext().getMetrics());
        MerkleMapMetrics.register(platform.getContext().getMetrics());
    }

    private void generateEvents() {
        final long now = System.nanoTime();
        final double tps = (double) transPerSecToCreate
                / (double) platform.getAddressBook().getSize();
        int numCreated = 0;

        if (transPerSecToCreate > -1) { // if not unlimited (-1 means unlimited)
            toCreate += ((double) now - lastEventTime) * NANOSECONDS_TO_SECONDS * tps;
        }

        lastEventTime = now;
        try {
            while (transactionsCreated < maximumTransactionsPerNode) {
                if (transPerSecToCreate > -1 && toCreate < 1) {
                    break; // don't create too many transactions per second
                }

                if (transPerEventMax > -1 && numCreated >= transPerEventMax) {
                    break; // don't create too many transactions per event
                }

                final byte[] transactionData = generator.generateTransaction();

                while (!platform.createTransaction(transactionData)) {
                    Thread.sleep(1_000);
                }

                transactionsCreated++;

                numCreated++;
                toCreate--;

                throttleTransactionCreation();
            }
        } catch (final SignatureException | InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void throttleTransactionCreation() throws InterruptedException {
        Thread.sleep(50);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SwirldState newState() {
        return new MigrationTestingToolState();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BasicSoftwareVersion getSoftwareVersion() {
        return softwareVersion;
    }
}
