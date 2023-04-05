/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
 * This payload is logged after a reconnect is completed.
 */
public class ReconnectDataUsagePayload extends AbstractLogPayload {

    private double dataMegabytes;

    public ReconnectDataUsagePayload() {}

    /**
     * @param message
     * 		the human readable message
     * @param dataMegabytes
     * 		the amount of data transmitted during execution of the reconnect
     */
    public ReconnectDataUsagePayload(final String message, final double dataMegabytes) {
        super(message);
        this.dataMegabytes = dataMegabytes;
    }

    public double getDataMegabytes() {
        return dataMegabytes;
    }

    public void setDataMegabytes(double dataMegabytes) {
        this.dataMegabytes = dataMegabytes;
    }
}
