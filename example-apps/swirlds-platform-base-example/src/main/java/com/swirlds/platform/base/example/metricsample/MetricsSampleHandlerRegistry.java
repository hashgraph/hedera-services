/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.base.example.metricsample;

import com.swirlds.metrics.api.DoubleGauge;
import com.swirlds.platform.base.example.ext.BaseContext;
import com.swirlds.platform.base.example.server.HttpHandlerDefinition;
import com.swirlds.platform.base.example.server.HttpHandlerRegistry;
import com.swirlds.platform.base.example.server.PostTriggerHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Creates a simpler handler that updates the value of a metric
 */
public class MetricsSampleHandlerRegistry implements HttpHandlerRegistry {

    record UpdateGauge(double value) {}

    @Override
    @NonNull
    public Set<HttpHandlerDefinition> handlers(final @NonNull BaseContext context) {
        DoubleGauge gauge = context.metrics().getOrCreate(new DoubleGauge.Config("sample", "test_gauge"));

        Consumer<UpdateGauge> updateGauge = body -> {
            gauge.set(body.value);
        };

        return Set.of(new PostTriggerHandler<>("metrics/updateGauge", context, UpdateGauge.class, updateGauge));
    }
}
