/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.system;

import static com.swirlds.logging.LogMarker.EXCEPTION;

import com.swirlds.logging.payloads.SystemExitPayload;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class SystemUtils {
    private static final Logger logger = LogManager.getLogger(SystemUtils.class);

    private SystemUtils() {}

    /**
     * Exits the system
     *
     * @param reason
     * 		the reason for the exit
     * @param haltRuntime
     * 		whether to halt the java runtime or not
     */
    public static void exitSystem(final SystemExitReason reason, final boolean haltRuntime) {
        if (reason.isError()) {
            logger.error(EXCEPTION.getMarker(), new SystemExitPayload(reason.name(), reason.getExitCode()));
            final String exitMsg = "Exiting system, reason: " + reason;
            System.out.println(exitMsg);
        }
        System.exit(reason.getExitCode());
        if (haltRuntime) {
            Runtime.getRuntime().halt(reason.getExitCode());
        }
    }

    /**
     * Same as {@link #exitSystem(SystemExitReason, boolean)}, but with haltRuntime set to false
     *
     * @see #exitSystem(SystemExitReason, boolean)
     */
    public static void exitSystem(final SystemExitReason reason) {
        exitSystem(reason, false);
    }
}
