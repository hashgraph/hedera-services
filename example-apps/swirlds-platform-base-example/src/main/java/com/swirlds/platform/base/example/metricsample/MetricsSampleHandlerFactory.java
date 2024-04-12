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
import com.swirlds.platform.base.example.BaseContext;
import com.swirlds.platform.base.example.server.HttpHandlerDefinition;
import com.swirlds.platform.base.example.server.HttpHandlerFactory;
import com.swirlds.platform.base.example.server.PostTriggerHandler;
import java.util.Set;
import java.util.function.Consumer;

public class MetricsSampleHandlerFactory implements HttpHandlerFactory {

    record UpdateGauge(double value) {}

    @Override
    public Set<HttpHandlerDefinition> initAndCreate(BaseContext context) {
        DoubleGauge gauge = context.metrics().getOrCreate(new DoubleGauge.Config("sample", "test-gauge"));

        Consumer<UpdateGauge> updateGauge = body -> {
            gauge.set(body.value);
        };

        return Set.of(new PostTriggerHandler<>("/metrics/updateGauge", UpdateGauge.class, updateGauge));
    }
}
