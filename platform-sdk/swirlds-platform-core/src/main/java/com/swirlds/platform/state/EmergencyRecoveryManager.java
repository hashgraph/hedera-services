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

package com.swirlds.platform.state;

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.platform.system.SystemExitReason.EMERGENCY_RECOVERY_ERROR;

import com.swirlds.platform.dispatch.triggers.control.ShutdownRequestedTrigger;
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
    private volatile boolean emergencyStateRequired;

    /**
     * @param shutdownRequestedTrigger
     * 		a trigger that requests the platform to shut down
     * @param emergencyRecoveryDir
     * 		the directory to look for an emergency recovery file in
     */
    public EmergencyRecoveryManager(
            final ShutdownRequestedTrigger shutdownRequestedTrigger, final Path emergencyRecoveryDir) {
        this.shutdownRequestedTrigger = shutdownRequestedTrigger;
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
    public EmergencyRecoveryFile getEmergencyRecoveryFile() {
        return emergencyRecoveryFile;
    }

    private EmergencyRecoveryFile readEmergencyRecoveryFile(final Path dir) {
        try {
            return EmergencyRecoveryFile.read(dir);
        } catch (final IOException e) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "Detected an emergency recovery file at {} but was unable to read it",
                    dir,
                    e);
            shutdownRequestedTrigger.dispatch("Emergency Recovery Error", EMERGENCY_RECOVERY_ERROR.getExitCode());
            return null;
        }
    }
}
