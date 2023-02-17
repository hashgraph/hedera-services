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

package com.hedera.services.bdd.spec.utilops.throughput;

import com.google.common.base.MoreObjects;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.stats.ThroughputObs;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import java.util.Optional;

public class StartThroughputObs extends UtilOp {
    private final String name;
    Optional<Long> expectedTimeToSaturateQueue = Optional.empty();

    public StartThroughputObs(String name) {
        this.name = name;
    }

    public StartThroughputObs msToSaturateQueues(long period) {
        expectedTimeToSaturateQueue = Optional.of(period);
        return this;
    }

    @Override
    protected boolean submitOp(HapiSpec spec) {
        long saturationTime = System.currentTimeMillis()
                + expectedTimeToSaturateQueue.orElse(spec.setup().defaultQueueSaturationMs());
        ThroughputObs baseObs = new ThroughputObs(name, saturationTime, spec);
        spec.registry().saveThroughputObs(baseObs);
        return false;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("starting", name).toString();
    }
}
