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

package com.swirlds.logging.payloads;

/**
 * This payload is logged when an application calls the init() method.
 */
public class SoftwareVersionPayload extends AbstractLogPayload {

    private String trigger;
    private String previousSoftwareVersion;

    /**
     * Zero arg constructor, required by log payload framework.
     */
    public SoftwareVersionPayload() {}

    /**
     * @param message
     * 		a human readable message
     * @param trigger
     * 		describes the reason why the state was created/recreated
     * @param previousSoftwareVersion
     * 		the previous version of the software, as a String.
     */
    public SoftwareVersionPayload(final String message, final String trigger, final String previousSoftwareVersion) {
        super(message);
        this.trigger = trigger;
        this.previousSoftwareVersion = previousSoftwareVersion;
    }

    public String getTrigger() {
        return trigger;
    }

    public void setTrigger(final String trigger) {
        this.trigger = trigger;
    }

    public String getPreviousSoftwareVersion() {
        return previousSoftwareVersion;
    }

    public void setPreviousSoftwareVersion(final String previousSoftwareVersion) {
        this.previousSoftwareVersion = previousSoftwareVersion;
    }
}
