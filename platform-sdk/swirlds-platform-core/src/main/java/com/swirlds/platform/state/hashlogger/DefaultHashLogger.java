/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state.hashlogger;

import static com.swirlds.logging.legacy.LogMarker.STATE_HASH;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.platform.config.StateConfig;
import com.swirlds.platform.state.MerkleRoot;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.MessageFactory;
import org.apache.logging.log4j.message.ParameterizedMessageFactory;

/**
 * A default implementation of a {@link HashLogger}.
 */
public class DefaultHashLogger implements HashLogger {

    private static final Logger logger = LogManager.getLogger(DefaultHashLogger.class);

    private static final MessageFactory MESSAGE_FACTORY = ParameterizedMessageFactory.INSTANCE;
    private final AtomicLong lastRoundLogged = new AtomicLong(-1);
    private final int depth;
    private final Logger logOutput; // NOSONAR: selected logger to output to.
    private final boolean isEnabled;

    /**
     * Construct a HashLogger.
     *
     * @param platformContext the platform context
     */
    public DefaultHashLogger(@NonNull final PlatformContext platformContext) {
        this(platformContext, logger);
    }

    /**
     * Internal constructor visible for testing.
     *
     * @param platformContext the platform context
     * @param logOutput       the logger to write to
     */
    DefaultHashLogger(@NonNull final PlatformContext platformContext, @NonNull final Logger logOutput) {
        final StateConfig stateConfig = platformContext.getConfiguration().getConfigData(StateConfig.class);
        depth = stateConfig.debugHashDepth();
        isEnabled = stateConfig.enableHashStreamLogging();
        this.logOutput = Objects.requireNonNull(logOutput);
    }

    /**
     * Delegates extraction and logging of the signed state's hashes
     *
     * @param reservedState the signed state to retrieve hash information from and log.
     */
    public void logHashes(@NonNull final ReservedSignedState reservedState) {
        try (reservedState) {
            if (!isEnabled) {
                return;
            }

            final SignedState signedState = reservedState.get();

            final long currentRound = signedState.getRound();
            final long prevRound = lastRoundLogged.getAndUpdate(value -> Math.max(value, currentRound));

            if (prevRound >= 0 && currentRound - prevRound > 1) {
                // One or more rounds skipped.
                logOutput.info(
                        STATE_HASH.getMarker(),
                        () -> MESSAGE_FACTORY.newMessage(
                                "*** Several rounds skipped. Round received {}. Previously received {}.",
                                currentRound,
                                prevRound));
            }

            if (currentRound > prevRound) {
                logOutput.info(STATE_HASH.getMarker(), () -> generateLogMessage(signedState));
            }
        }
    }

    /**
     * Generate the actual log message. Packaged in a lambda in case the logging framework decides not to log it.
     *
     * @param signedState the signed state to log
     * @return the log message
     */
    @NonNull
    private Message generateLogMessage(@NonNull final SignedState signedState) {
        final MerkleRoot state = signedState.getState();
        final String platformInfo = state.getInfoString(depth);

        return MESSAGE_FACTORY.newMessage(
                """
                        State Info, round = {}:
                        {}""",
                signedState.getRound(),
                platformInfo);
    }
}
