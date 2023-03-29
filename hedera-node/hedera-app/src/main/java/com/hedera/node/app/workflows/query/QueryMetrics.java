/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.app.workflows.query;

import static com.hedera.node.app.spi.HapiUtils.QUERY_FUNCTIONS;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.swirlds.common.metrics.Counter;
import com.swirlds.common.metrics.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.EnumMap;
import java.util.Map;
import javax.inject.Inject;

/**
 * A container for metrics related to queries.
 */
public class QueryMetrics {

    /** A map of counter metrics for each type of query received */
    private final Map<HederaFunctionality, Counter> received = new EnumMap<>(HederaFunctionality.class);
    /** A map of counter metrics for each type of query answered */
    private final Map<HederaFunctionality, Counter> answered = new EnumMap<>(HederaFunctionality.class);

    /**
     * Constructor of {@code QueryMetrics}
     *
     * @param metrics entry-point to the metrics-system
     */
    @Inject
    public QueryMetrics(@NonNull final Metrics metrics) {
        // Create metrics for tracking each query received and answered per query type
        for (var function : QUERY_FUNCTIONS) {
            var name = function.name() + "Received";
            var desc = "The number of queries received for " + function.name();
            received.put(function, metrics.getOrCreate(new Counter.Config("app", name).withDescription(desc)));

            name = function.name() + "Answered";
            desc = "The number of queries answered for " + function.name();
            answered.put(function, metrics.getOrCreate(new Counter.Config("app", name).withDescription(desc)));
        }
    }

    public void countReceived(@NonNull final HederaFunctionality functionality) {
        safeIncrement(received.get(functionality));
    }

    public void countAnswered(@NonNull final HederaFunctionality functionality) {
        safeIncrement(answered.get(functionality));
    }

    private static void safeIncrement(@Nullable final Counter counter) {
        if (counter != null) {
            counter.increment();
        }
    }
}
