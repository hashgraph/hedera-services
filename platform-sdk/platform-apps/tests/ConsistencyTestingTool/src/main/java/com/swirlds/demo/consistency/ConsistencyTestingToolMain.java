/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.demo.consistency;

import static com.swirlds.base.ArgumentUtils.throwArgNull;
import static com.swirlds.logging.LogMarker.STARTUP;

import com.swirlds.common.system.BasicSoftwareVersion;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.PlatformWithDeprecatedMethods;
import com.swirlds.common.system.SwirldMain;
import com.swirlds.common.system.SwirldState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A testing app for guaranteeing proper handling of transactions after a restart
 */
public class ConsistencyTestingToolMain implements SwirldMain {

    private static final Logger logger = LogManager.getLogger(ConsistencyTestingToolMain.class);

    /**
     * The default software version of this application
     */
    private static final BasicSoftwareVersion softwareVersion = new BasicSoftwareVersion(1);

    /**
     * The platform instance
     */
    private Platform platform;

    /**
     * Whether to permit gaps in round history
     */
    private final boolean permitRoundGaps;

    /**
     * The number of transactions to generate per second.
     */
    private static final int TRANSACTIONS_PER_SECOND = 100;

    /**
     * The name of the log file being written to / read from
     */
    private static final String LOG_FILE_NAME = "ConsistencyTestLog";

    /**
     * Constructor
     *
     * @param permitRoundGaps whether or not gaps in the round history will be permitted in the test. if false, an error
     *                        will be logged if a gap is found
     */
    public ConsistencyTestingToolMain(final boolean permitRoundGaps) {
        this.permitRoundGaps = permitRoundGaps;

        logger.info(STARTUP.getMarker(), "constructor called in Main.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(@NonNull final Platform platform, @NonNull final NodeId nodeId) {
        throwArgNull(platform, "platform");
        throwArgNull(nodeId, "nodeId");

        logger.info(STARTUP.getMarker(), "init called in Main for node {}.", nodeId);
        this.platform = platform;

        parseArguments(((PlatformWithDeprecatedMethods) platform).getParameters());
    }

    /**
     * Parses the arguments
     * <p>
     * Currently, no arguments are expected
     *
     * @param args the arguments
     * @throws IllegalArgumentException if the arguments array has length other than 0
     */
    private void parseArguments(@NonNull final String[] args) {
        Objects.requireNonNull(args, "The arguments must not be null.");
        if (args.length != 0) {
            throw new IllegalArgumentException("Expected no arguments. See javadocs for details.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run() {
        logger.info(STARTUP.getMarker(), "run called in Main.");
        new TransactionGenerator(new SecureRandom(), platform, TRANSACTIONS_PER_SECOND).start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SwirldState newState() {
        return new ConsistencyTestingToolState(
                permitRoundGaps, Path.of(System.getProperty("user.dir") + File.separator + LOG_FILE_NAME + ".csv"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BasicSoftwareVersion getSoftwareVersion() {
        logger.info(STARTUP.getMarker(), "returning software version {}", softwareVersion);
        return softwareVersion;
    }
}
