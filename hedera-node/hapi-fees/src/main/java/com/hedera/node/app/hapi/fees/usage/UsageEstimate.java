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
package com.hedera.node.app.hapi.fees.usage;

import static com.hedera.node.app.hapi.fees.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.HRS_DIVISOR;

import com.hederahashgraph.api.proto.java.FeeComponents;

public class UsageEstimate {
    static EstimatorUtils estimatorUtils = ESTIMATOR_UTILS;

    private long rbs;
    private long sbs;

    private final FeeComponents.Builder base;

    public UsageEstimate(final FeeComponents.Builder base) {
        this.base = base;
    }

    public void addRbs(final long amount) {
        rbs += amount;
    }

    public void addSbs(final long amount) {
        sbs += amount;
    }

    public FeeComponents.Builder base() {
        return base;
    }

    public FeeComponents build() {
        return base.setSbh(estimatorUtils.nonDegenerateDiv(sbs, HRS_DIVISOR))
                .setRbh(estimatorUtils.nonDegenerateDiv(rbs, HRS_DIVISOR))
                .build();
    }

    public long getRbs() {
        return rbs;
    }
}
