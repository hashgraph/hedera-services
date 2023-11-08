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

package com.swirlds.common.system;

public enum SystemExitCode {
    NO_ERROR(0),
    /**
     * Configuration error.
     */
    CONFIGURATION_ERROR(33),
    /**
     * This node encountered an ISS.
     */
    ISS(199),
    SAVED_STATE_NOT_LOADED(200),
    SWIRLD_MAIN_THREW_EXCEPTION(201),
    /**
     * This node has fallen behind but can not reconnect due to policy.
     */
    BEHIND_RECONNECT_DISABLED(202),
    /**
     * This node exceeded the maximum consecutive failed reconnect attempts.
     */
    RECONNECT_FAILURE(203),
    /**
     * An issue occurred while loading keys from .pfx files
     */
    KEY_LOADING_FAILED(204),
    /**
     * The machine IP addresses did not match any address in the address book.
     */
    NODE_ADDRESS_MISMATCH(205),
    /**
     * An error occurred during emergency recovery
     */
    EMERGENCY_RECOVERY_ERROR(206),
    /**
     * An exit was called but no code was supplied
     */
    NO_EXIT_CODE(254),
    /**
     * An unrecoverable error.
     */
    FATAL_ERROR(255);

    private final int exitCode;

    SystemExitCode(final int exitCode) {
        this.exitCode = exitCode;
    }

    public int getExitCode() {
        return exitCode;
    }

    public boolean isError() {
        return exitCode != 0;
    }
}
