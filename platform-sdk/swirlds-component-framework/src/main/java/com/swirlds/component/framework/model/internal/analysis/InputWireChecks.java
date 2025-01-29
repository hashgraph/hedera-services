/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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

package com.swirlds.component.framework.model.internal.analysis;

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.logging.legacy.LogMarker.STARTUP;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utilities for sanity checking input wires.
 */
public final class InputWireChecks {

    private static final Logger logger = LogManager.getLogger(InputWireChecks.class);

    private InputWireChecks() {}

    /**
     * Make sure every input wire was properly bound.
     *
     * @param inputWires      the input wires that were created
     * @param boundInputWires the input wires that were bound
     * @return true if there were unbound input wires, false otherwise
     */
    public static boolean checkForUnboundInputWires(
            @NonNull final Set<InputWireDescriptor> inputWires,
            @NonNull final Set<InputWireDescriptor> boundInputWires) {
        if (inputWires.size() == boundInputWires.size()) {
            logger.info(STARTUP.getMarker(), "All input wires have been bound.");
            return false;
        }

        final StringBuilder sb = new StringBuilder();
        sb.append("The following input wire(s) were created but not bound:\n");
        for (final InputWireDescriptor inputWire : inputWires) {
            if (!boundInputWires.contains(inputWire)) {
                sb.append("  - ")
                        .append("Input wire '")
                        .append(inputWire.name())
                        .append("' in scheduler '")
                        .append(inputWire.taskSchedulerName())
                        .append("'\n");
            }
        }

        logger.error(EXCEPTION.getMarker(), sb.toString());

        return true;
    }
}
