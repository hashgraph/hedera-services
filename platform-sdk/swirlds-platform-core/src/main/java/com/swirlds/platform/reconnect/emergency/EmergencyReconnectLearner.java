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

package com.swirlds.platform.reconnect.emergency;

import static com.swirlds.logging.LogMarker.RECONNECT;

import com.swirlds.platform.Connection;
import com.swirlds.platform.reconnect.ReconnectController;
import com.swirlds.platform.reconnect.ReconnectException;
import com.swirlds.platform.state.EmergencyRecoveryFile;
import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Executes emergency reconnect in the role of the learner
 */
public class EmergencyReconnectLearner {
    private static final Logger logger = LogManager.getLogger(EmergencyReconnectLearner.class);
    private final EmergencyRecoveryFile emergencyRecoveryFile;
    private final ReconnectController reconnectController;
    private final EmergencySignedStateValidator validator;

    /**
     * @param emergencyRecoveryFile
     * 		the emergency recovery file used for this reconnect
     * @param reconnectController
     * 		controls reconnecting as a learner
     */
    public EmergencyReconnectLearner(
            final EmergencyRecoveryFile emergencyRecoveryFile, final ReconnectController reconnectController) {
        this.emergencyRecoveryFile = emergencyRecoveryFile;
        this.reconnectController = reconnectController;
        validator = new EmergencySignedStateValidator(emergencyRecoveryFile);
    }

    /**
     * Performs emergency reconnect in the role of the learner using the given connection.
     *
     * @param connection
     * 		the connection to perform the reconnect on
     * @return {@code true} if the peer has a compatible state to send, {@code false} otherwise
     */
    public boolean execute(final Connection connection) {
        try {
            final boolean teacherHasState = teacherHasState(connection);
            if (teacherHasState) {
                logger.info(
                        RECONNECT.getMarker(),
                        "Peer {} has a compatible state. Continuing with emergency reconnect.",
                        connection.getOtherId());
                reconnectController.setStateValidator(validator);
                reconnectController.provideLearnerConnection(connection);
                return true;
            } else {
                logger.info(
                        RECONNECT.getMarker(),
                        "Peer {} does not have a compatible state. Attempting emergency reconnect with another peer.",
                        connection.getOtherId());
                return false;
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ReconnectException(e);
        } catch (final IOException e) {
            throw new ReconnectException(e);
        }
    }

    private boolean teacherHasState(final Connection connection) throws IOException {
        // Send the information for the state we need
        connection.getDos().writeLong(emergencyRecoveryFile.round());
        connection.getDos().writeSerializable(emergencyRecoveryFile.hash(), true);
        connection.getDos().flush();

        // Read the teacher's response indicating if it has a compatible state
        return connection.getDis().readBoolean();
    }
}
