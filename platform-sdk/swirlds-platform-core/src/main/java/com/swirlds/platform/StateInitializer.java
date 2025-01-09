/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.swirlds.platform;

import static com.swirlds.common.threading.interrupt.Uninterruptable.abortAndThrowIfInterrupted;
import static com.swirlds.logging.legacy.LogMarker.STARTUP;
import static com.swirlds.platform.system.InitTrigger.GENESIS;
import static com.swirlds.platform.system.InitTrigger.RESTART;
import static com.swirlds.platform.system.SoftwareVersion.NO_VERSION;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.platform.config.StateConfig;
import com.swirlds.platform.state.PlatformMerkleStateRoot;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.StateEventHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Encapsulates the logic for calling
 * {@link StateEventHandler#init(Platform, InitTrigger, SoftwareVersion)}
 * startup time.
 */
public final class StateInitializer {

    private static final Logger logger = LogManager.getLogger(StateInitializer.class);

    private StateInitializer() {}

    /**
     * Initialize the state.
     *
     * @param platform        the platform instance
     * @param platformContext the platform context
     * @param signedState     the state to initialize
     */
    public static void initializeState(
            @NonNull final Platform platform,
            @NonNull final PlatformContext platformContext,
            @NonNull final SignedState signedState) {

        final SoftwareVersion previousSoftwareVersion;
        final InitTrigger trigger;

        if (signedState.isGenesisState()) {
            previousSoftwareVersion = NO_VERSION;
            trigger = GENESIS;
        } else {
            previousSoftwareVersion =
                    signedState.getState().getReadablePlatformState().getCreationSoftwareVersion();
            trigger = RESTART;
        }

        final StateEventHandler initialStateEvenHandler = signedState.getStateEventHandler();
        final PlatformMerkleStateRoot stateRoot = initialStateEvenHandler.getStateRoot();

        // Although the state from disk / genesis state is initially hashed, we are actually dealing with a copy
        // of that state here. That copy should have caused the hash to be cleared.

        if (stateRoot.getHash() != null) {
            throw new IllegalStateException("Expected initial state to be unhashed");
        }

        initialStateEvenHandler.init(platform, trigger, previousSoftwareVersion);

        abortAndThrowIfInterrupted(stateRoot::computeHash, "interrupted while attempting to hash the state");

        // If our hash changes as a result of the new address book then our old signatures may become invalid.
        signedState.pruneInvalidSignatures();

        final StateConfig stateConfig = platformContext.getConfiguration().getConfigData(StateConfig.class);
        logger.info(
                STARTUP.getMarker(),
                """
                        The platform is using the following initial state:
                        {}""",
                signedState.getState().getInfoString(stateConfig.debugHashDepth()));
    }
}
