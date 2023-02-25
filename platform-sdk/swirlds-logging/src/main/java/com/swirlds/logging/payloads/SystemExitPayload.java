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

package com.swirlds.logging.payloads;

/**
 * This payload is logged when the browser shuts down the JVM.
 */
public class SystemExitPayload extends AbstractLogPayload {

    /**
     * The reason why the system is exiting.
     */
    private String reason;

    /**
     * THe system exit code.
     */
    private int code;

    public SystemExitPayload() {
        super("Exiting system");
    }

    public SystemExitPayload(final String reason, final int code) {
        this();
        this.reason = reason;
        this.code = code;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(final String reason) {
        this.reason = reason;
    }

    public int getCode() {
        return code;
    }

    public void setCode(final int code) {
        this.code = code;
    }
}
