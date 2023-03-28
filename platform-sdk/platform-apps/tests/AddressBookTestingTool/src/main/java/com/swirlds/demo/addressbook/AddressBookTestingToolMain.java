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

package com.swirlds.demo.addressbook;

import static com.swirlds.logging.LogMarker.STARTUP;

import com.swirlds.common.system.BasicSoftwareVersion;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.PlatformWithDeprecatedMethods;
import com.swirlds.common.system.SwirldMain;
import com.swirlds.common.system.SwirldState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.security.SecureRandom;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * <p>
 * An application that updates address book stake weights on version upgrade.
 * </p>
 *
 * <p>
 * Arguments:
 * <ol>
 * <li>
 * No arguments parsed at this time.  The software version must be updated through setting the static value in
 * this main class and recompiling. The behavior of staking is updated in the State class and recompiling.
 * </li>
 * </ol>
 */
public class AddressBookTestingToolMain implements SwirldMain {
    /** The logger for this class. */
    @NonNull
    private static final Logger logger = LogManager.getLogger(AddressBookTestingToolMain.class);

    /** The default software version of this application. */
    @NonNull
    private static final BasicSoftwareVersion softwareVersion = new BasicSoftwareVersion(1);

    /** The platform. */
    @NonNull
    private Platform platform;

    /** The number of transactions to generate per second. */
    private static final int TRANSACTIONS_PER_SECOND = 100;

    public AddressBookTestingToolMain() {
        // default constructor, does nothing
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(@NonNull final Platform platform, @NonNull final NodeId id) {
        Objects.requireNonNull(platform, "The platform must not be null.");
        Objects.requireNonNull(id, "The node id must not be null.");

        logger.info(STARTUP.getMarker(), "init called in Main for node {}.", id);
        this.platform = platform;

        parseArguments(((PlatformWithDeprecatedMethods) platform).getParameters());
    }

    /**
     * Parses the arguments.  Currently, no arguments are expected.
     *
     * @param args the arguments
     * @throws IllegalArgumentException if the arguments array has length other than 0.
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
        return new AddressBookTestingToolState();
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
