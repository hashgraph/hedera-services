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

package com.swirlds.platform.recovery;

import static com.swirlds.common.system.SystemExitCode.EMERGENCY_RECOVERY_ERROR;
import static com.swirlds.logging.LogMarker.EXCEPTION;

import com.swirlds.common.config.StateConfig;
import com.swirlds.platform.dispatch.triggers.control.ShutdownRequestedTrigger;
import com.swirlds.platform.recovery.emergencyfile.EmergencyRecoveryFile;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Contains the current state of emergency recovery.
 */
public class EmergencyRecoveryManager {
    private static final Logger logger = LogManager.getLogger(EmergencyRecoveryManager.class);
    private final ShutdownRequestedTrigger shutdownRequestedTrigger;
    private final EmergencyRecoveryFile emergencyRecoveryFile;
    private final StateConfig stateConfig;
    private volatile boolean emergencyStateRequired;

    /**
     * A "normal startup" is when a node comes online and the most recent state on disk is the state that is loaded into
     * the system.
     *
     * <p>
     * During emergency recovery, nodes sometimes need to load a state from disk that is not the most recent state, or
     * they need to obtain a state using emergency reconnect.
     */
    private boolean emergencyStartup = false;

    /**
     * @param stateConfig              the state configuration from the platform
     * @param shutdownRequestedTrigger a trigger that requests the platform to shut down
     * @param emergencyRecoveryDir     the directory to look for an emergency recovery file in
     */
    public EmergencyRecoveryManager(
            @NonNull final StateConfig stateConfig,
            @NonNull final ShutdownRequestedTrigger shutdownRequestedTrigger,
            @NonNull final Path emergencyRecoveryDir) {
        this.stateConfig = stateConfig;
        this.shutdownRequestedTrigger = shutdownRequestedTrigger;
        this.emergencyRecoveryFile = readEmergencyRecoveryFile(emergencyRecoveryDir);
        emergencyStateRequired = emergencyRecoveryFile != null;
    }

    /**
     * Signals that the node is starting up in emergency recovery mode. A node is considered to be in emergency recovery
     * mode if it is required to load a state from disk that is not the most recent state (by round number), or if it is
     * required to obtain a state using emergency reconnect.
     */
    public void signalEmergencyStartup() {
        emergencyStartup = true;
    }

    /**
     * Check if the node is in emergency startup mode. A node is considered to be in emergency startup mode if it is
     * required to load a state from disk that is not the most recent state (by round number), or if it is required to
     * obtain a state using emergency reconnect.
     *
     * @return {@code true} if the node is in emergency startup mode, {@code false} otherwise
     */
    public boolean isInEmergencyStartupMode() {
        return emergencyStartup;
    }

    /**
     * Returns whether an emergency state is required to start the node. The state can be loaded from disk or acquired
     * via an emergency reconnect.
     *
     * @return {@code true} if an emergency recovery state is required, {@code false} otherwise
     */
    public boolean isEmergencyStateRequired() {
        return emergencyStateRequired;
    }

    /**
     * Invoked when an emergency state has been loaded into the system.
     */
    public void emergencyStateLoaded() {
        emergencyStateRequired = false;
    }

    /**
     * Provides the emergency recovery file, or null if there was none at node boot time.
     *
     * @return the emergency recovery files, or null if none
     */
    public EmergencyRecoveryFile getEmergencyRecoveryFile() {
        return emergencyRecoveryFile;
    }

    private EmergencyRecoveryFile readEmergencyRecoveryFile(final Path dir) {
        try {
            return EmergencyRecoveryFile.read(stateConfig, dir);
        } catch (final IOException e) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "Detected an emergency recovery file at {} but was unable to read it",
                    dir,
                    e);
            shutdownRequestedTrigger.dispatch("Emergency Recovery Error", EMERGENCY_RECOVERY_ERROR);
            return null;
        }
    }
}
