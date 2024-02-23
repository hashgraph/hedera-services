/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.logging.extensions.emergency;

import com.swirlds.logging.internal.emergency.EmergencyLoggerImpl;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * This class is used to get an instance of the emergency logger.
 */
public final class EmergencyLoggerProvider {

    /**
     * Private constructor to prevent instantiation.
     */
    private EmergencyLoggerProvider() {}

    /**
     * Gets an instance of the emergency logger.
     *
     * @return an instance of the emergency logger
     */
    @NonNull
    public static EmergencyLogger getEmergencyLogger() {
        return EmergencyLoggerImpl.getInstance();
    }
}
