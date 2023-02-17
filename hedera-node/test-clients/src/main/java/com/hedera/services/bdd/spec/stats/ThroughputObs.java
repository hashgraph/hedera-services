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

package com.hedera.services.bdd.spec.stats;

import com.hedera.services.bdd.spec.HapiSpec;

public class ThroughputObs {
    private int numOpsAtObservationStart;
    private int numOpsAtObservationFinish = -1;
    private int numOpsAtExpectedQueueSaturation = -1;
    private long obsLengthMs = -1L;
    private final long creationTime;
    private final long expectedQueueSaturationTime;
    private final String name;

    public ThroughputObs(String name, long expectedQueueSaturationTime, HapiSpec spec) {
        this.name = name;
        this.creationTime = System.currentTimeMillis();
        this.expectedQueueSaturationTime = expectedQueueSaturationTime;
        spec.addLedgerOpCountCallback(count -> {
            if (numOpsAtExpectedQueueSaturation == -1) {
                long now = System.currentTimeMillis();
                if (now >= expectedQueueSaturationTime) {
                    numOpsAtExpectedQueueSaturation = count;
                }
            }
        });
        this.numOpsAtObservationStart = spec.numLedgerOps();
    }

    public long getCreationTime() {
        return creationTime;
    }

    public void setObsLengthMs(long obsLengthMs) {
        this.obsLengthMs = obsLengthMs;
    }

    public void setNumOpsAtObservationFinish(int numOpsAtObservationFinish) {
        this.numOpsAtObservationFinish = numOpsAtObservationFinish;
    }

    public long getExpectedQueueSaturationTime() {
        return expectedQueueSaturationTime;
    }

    public int getNumOpsAtExpectedQueueSaturation() {
        return numOpsAtExpectedQueueSaturation;
    }

    public int getNumOpsAtObservationStart() {
        return numOpsAtObservationStart;
    }

    public String getName() {
        return name;
    }

    public String summary() {
        if (!summarizable()) {
            return "Cannot be summarized, incomplete observation!";
        } else {
            int n = (numOpsAtObservationFinish - numOpsAtExpectedQueueSaturation);
            double opsPerSecond = (double) n / obsLengthMs * 1_000L;
            return String.format("~%.2f ledger ops/sec", opsPerSecond);
        }
    }

    private boolean summarizable() {
        return pos(numOpsAtExpectedQueueSaturation) && pos(numOpsAtObservationFinish) && pos(obsLengthMs);
    }

    private boolean pos(long v) {
        return v > 0L;
    }
}
