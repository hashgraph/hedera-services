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

package com.swirlds.platform.recovery;

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.platform.system.SystemExitCode.EMERGENCY_RECOVERY_ERROR;

import com.swirlds.platform.config.StateConfig;
import com.swirlds.platform.recovery.emergencyfile.EmergencyRecoveryFile;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.system.Shutdown;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Contains the current state of emergency recovery.
 */
public class EmergencyRecoveryManager {
    private static final Logger logger = LogManager.getLogger(EmergencyRecoveryManager.class);

    private final EmergencyRecoveryFile emergencyRecoveryFile;
    private final StateConfig stateConfig;
    private volatile boolean emergencyStateRequired;

    /**
     * @param stateConfig          the state configuration from the platform
     * @param emergencyRecoveryDir the directory to look for an emergency recovery file in
     */
    public EmergencyRecoveryManager(@NonNull final StateConfig stateConfig, @NonNull final Path emergencyRecoveryDir) {

        this.stateConfig = stateConfig;
        this.emergencyRecoveryFile = readEmergencyRecoveryFile(emergencyRecoveryDir);
        emergencyStateRequired = emergencyRecoveryFile != null;
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
    public @Nullable EmergencyRecoveryFile getEmergencyRecoveryFile() {
        return emergencyRecoveryFile;
    }

    /**
     * Returns whether the given state is the emergency state, if there even is one.
     *
     * @param state the state to check
     * @return {@code true} if there is an emergency state and the state supplied is the emergency state, {@code false}
     * otherwise
     */
    public boolean isEmergencyState(@NonNull final SignedState state) {
        if (emergencyRecoveryFile == null) {
            return false;
        }
        return state.getState().getHash().equals(emergencyRecoveryFile.hash())
                && state.getRound() == emergencyRecoveryFile.round();
    }

    private @Nullable EmergencyRecoveryFile readEmergencyRecoveryFile(final Path dir) {
        try {
            return EmergencyRecoveryFile.read(stateConfig, dir);
        } catch (final IOException e) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "Detected an emergency recovery file at {} but was unable to read it",
                    dir,
                    e);

            new Shutdown().shutdown("Emergency Recovery Error", EMERGENCY_RECOVERY_ERROR);
            return null;
        }
    }
}
