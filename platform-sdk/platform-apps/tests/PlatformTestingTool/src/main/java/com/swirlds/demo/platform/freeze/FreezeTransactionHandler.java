/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.demo.platform.freeze;

import com.swirlds.common.system.SwirldDualState;
import com.swirlds.demo.platform.fs.stresstest.proto.FreezeTransaction;
import java.time.Instant;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

public class FreezeTransactionHandler {
    private static final Logger logger = LogManager.getLogger(FreezeTransactionHandler.class);
    private static final Marker LOGM_FREEZE = MarkerManager.getMarker("FREEZE");

    public static boolean freeze(final FreezeTransaction transaction, final SwirldDualState swirldDualState) {
        logger.debug(LOGM_FREEZE, "Handling FreezeTransaction: " + transaction);
        try {
            swirldDualState.setFreezeTime(Instant.ofEpochSecond(transaction.getStartTimeEpochSecond()));
            return true;
        } catch (IllegalArgumentException ex) {
            logger.warn(LOGM_FREEZE, "FreezeTransactionHandler::freeze fails. {}", ex.getMessage());
            return false;
        }
    }
}
