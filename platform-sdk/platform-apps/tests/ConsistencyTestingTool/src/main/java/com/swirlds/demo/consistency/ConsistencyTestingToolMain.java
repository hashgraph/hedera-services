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

import static com.swirlds.logging.LogMarker.STARTUP;

import com.swirlds.common.system.BasicSoftwareVersion;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.SwirldMain;
import com.swirlds.common.system.SwirldState;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.security.SecureRandom;
import java.util.List;
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
    private static final SoftwareVersion softwareVersion = new BasicSoftwareVersion(1);

    /**
     * The platform instance
     */
    private Platform platform;

    /**
     * The number of transactions to generate per second.
     */
    private static final int TRANSACTIONS_PER_SECOND = 100;

    /**
     * Constructor
     */
    public ConsistencyTestingToolMain() {
        logger.info(STARTUP.getMarker(), "constructor called in Main.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(@NonNull final Platform platform, @NonNull final NodeId nodeId) {
        Objects.requireNonNull(nodeId);

        this.platform = Objects.requireNonNull(platform);

        logger.info(STARTUP.getMarker(), "init called in Main for node {}.", nodeId);
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
    @NonNull
    public SwirldState newState() {
        return new ConsistencyTestingToolState();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public SoftwareVersion getSoftwareVersion() {
        logger.info(STARTUP.getMarker(), "returning software version {}", softwareVersion);
        return softwareVersion;
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public List<Class<? extends Record>> getConfigDataTypes() {
        return List.of(ConsistencyTestingToolConfig.class);
    }
}
