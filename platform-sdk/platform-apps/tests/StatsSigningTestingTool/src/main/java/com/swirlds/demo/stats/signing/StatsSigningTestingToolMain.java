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

package com.swirlds.demo.stats.signing;
/*
 * This file is public domain.
 *
 * SWIRLDS MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
 * THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, OR NON-INFRINGEMENT. SWIRLDS SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 */

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static com.swirlds.common.utility.Units.NANOSECONDS_TO_MICROSECONDS;
import static com.swirlds.common.utility.Units.NANOSECONDS_TO_SECONDS;
import static com.swirlds.logging.LogMarker.STARTUP;

import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.common.system.BasicSoftwareVersion;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.PlatformWithDeprecatedMethods;
import com.swirlds.common.system.SwirldMain;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.threading.framework.StoppableThread;
import com.swirlds.common.threading.framework.config.StoppableThreadConfiguration;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.demo.stats.signing.algorithms.ECSecP256K1Algorithm;
import com.swirlds.demo.stats.signing.algorithms.X25519SigningAlgorithm;
import com.swirlds.platform.Browser;
import com.swirlds.platform.gui.SwirldsGui;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This demo collects statistics on the running of the network and consensus systems. It writes them to the screen, and
 * also saves them to disk in a comma separated value (.csv) file. Each transaction is 100 random bytes. So
 * StatsSigningDemoState.handleTransaction doesn't actually do anything.
 */
public class StatsSigningTestingToolMain implements SwirldMain {
    // the first four come from the parameters in the config.txt file

    private static final Logger logger = LogManager.getLogger(StatsSigningTestingToolMain.class);
    /**
     * the time of the last measurement of TPS
     */
    long lastTPSMeasureTime = 0;
    /**
     * number of events needed to be created (the non-integer leftover from last preEvent call
     */
    double toCreate = 0;
    /**
     * should this run with no windows?
     */
    private boolean headless = false;
    /**
     * bytes in each transaction
     */
    private int bytesPerTrans = 100;
    /**
     * create at most this many transactions in preEvent, even if more is needed to meet target rate
     */
    private int transPerEventMax = 2048;
    /**
     * transactions in each Event
     */
    private int transPerSecToCreate = 100;
    /**
     * the size of the signed transaction pool
     */
    private int signedTransPoolSize = 1024;
    /**
     * ID number for this member
     */
    private long selfId;
    /**
     * the app is run by this
     */
    private Platform platform;

    private TransactionPool transactionPool;

    private static final BasicSoftwareVersion softwareVersion = new BasicSoftwareVersion(1);

    private final StoppableThread transactionGenerator;

    private static final SpeedometerMetric.Config TRAN_SUBMIT_TPS_SPEED_CONFIG =
            new SpeedometerMetric.Config("Debug.info", "tranSubTPS").withDescription("Transaction submitted TPS");

    private SpeedometerMetric transactionSubmitSpeedometer;

    static final int TPS_MEASURE_PERIOD_IN_MILLISECONDS = 200;

    /**
     * This is just for debugging: it allows the app to run in Eclipse. If the config.txt exists and lists a particular
     * SwirldMain class as the one to run, then it can run in Eclipse (with the green triangle icon).
     *
     * @param args
     * 		these are not used
     */
    public static void main(final String[] args) {
        Browser.launch(args);
    }

    public StatsSigningTestingToolMain() {
        transactionGenerator = new StoppableThreadConfiguration<>(getStaticThreadManager())
                .setComponent("demo")
                .setThreadName("transaction-generator")
                .setMaximumRate(50)
                .setWork(this::generateTransactions)
                .build();
    }

    @Override
    public void init(final Platform platform, final NodeId id) {

        if (!(platform instanceof PlatformWithDeprecatedMethods)) {
            // Don't bother with setup while in recovery mode
            return;
        }

        final long syncDelay;
        this.platform = platform;
        selfId = id.getId();
        // parse the config.txt parameters, and allow optional _ as in 1_000_000
        final String[] parameters = ((PlatformWithDeprecatedMethods) platform).getParameters();
        headless = (parameters[0].equals("1"));
        syncDelay = Integer.parseInt(parameters[2].replaceAll("_", ""));
        bytesPerTrans = Integer.parseInt(parameters[3].replaceAll("_", ""));
        transPerEventMax = Integer.parseInt(parameters[4].replaceAll("_", ""));
        transPerSecToCreate = Integer.parseInt(parameters[5].replaceAll("_", ""));

        // If we have a 7th setting, treat it as the signedTransPoolSize
        if (parameters.length > 6) {
            signedTransPoolSize = Integer.parseInt(parameters[6].replaceAll("_", ""));
        }

        if (transPerEventMax == -1 && transPerSecToCreate == -1) {
            // they shouldn't both be -1, so set one of them
            transPerEventMax = 1024;
        }
        SwirldsGui.setAbout(
                platform.getSelfId().getId(),
                "Stats Signing Demo v. 1.3\nThis writes statistics to a log file,"
                        + " such as the number of transactions per second.");
        ((PlatformWithDeprecatedMethods) platform).setSleepAfterSync(syncDelay);

        transactionPool = new TransactionPool(
                platform.getSelfId().getId(),
                signedTransPoolSize,
                bytesPerTrans,
                true,
                new ECSecP256K1Algorithm(),
                new X25519SigningAlgorithm());

        final Metrics metrics = platform.getContext().getMetrics();
        transactionSubmitSpeedometer = metrics.getOrCreate(TRAN_SUBMIT_TPS_SPEED_CONFIG);
    }

    @Override
    public void run() {
        final Thread shutdownHook = new ThreadConfiguration(getStaticThreadManager())
                .setDaemon(false)
                .setNodeId(platform.getSelfId().getId())
                .setComponent("app")
                .setThreadName("demo_log_time_pulse")
                .setRunnable(() -> {
                    final Logger logger = LogManager.getLogger(getClass());
                    logger.debug(STARTUP.getMarker(), "Keepalive Event for Regression Timing");
                })
                .build();

        Runtime.getRuntime().addShutdownHook(shutdownHook);

        transactionGenerator.start();

        while (true) {
            try {
                Thread.sleep(1000);
                generateTransactions();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private synchronized void generateTransactions() {
        byte[] transaction;
        final long now = System.nanoTime();
        final double tps =
                transPerSecToCreate / (double) platform.getAddressBook().getSize();
        int numCreated = 0;

        // if it's first time calling this, just set lastEventTime and return
        // so the period from app start to the first time init() is called is ignored
        // to avoid a huge burst of transactions at the start of the test
        if (lastTPSMeasureTime == 0) {
            lastTPSMeasureTime = now;
            logger.info(
                    STARTUP.getMarker(), "First time calling generateTransactions() Expected TPS per code is {}", tps);
            return;
        }

        if (transPerSecToCreate > -1) { // if not unlimited (-1 means unlimited)
            // to get stable TPS output, use a large measure window
            if (((double) now - lastTPSMeasureTime) * NANOSECONDS_TO_MICROSECONDS
                    > TPS_MEASURE_PERIOD_IN_MILLISECONDS) {
                toCreate += ((double) now - lastTPSMeasureTime) * NANOSECONDS_TO_SECONDS * tps;
                lastTPSMeasureTime = now;
            }
        }

        while (true) {
            if (transPerSecToCreate > -1 && toCreate < 1) {
                break; // don't create too many transactions per second
            }
            if (transPerEventMax > -1 && numCreated >= transPerEventMax) {
                break; // don't create too many transactions per event
            }

            // Retrieve a random signed transaction from the pool
            transaction = transactionPool.transaction();

            if (!platform.createTransaction(transaction)) {
                break; // if the queue is full, the stop adding to it
            }

            numCreated++;
            toCreate--;
        }
        transactionSubmitSpeedometer.update(numCreated);
        // toCreate will now represent any leftover transactions that we
        // failed to create this time, and will create next time
    }

    @Override
    public SwirldState newState() {
        return new StatsSigningTestingToolState(selfId, () -> transactionPool);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BasicSoftwareVersion getSoftwareVersion() {
        return softwareVersion;
    }
}
