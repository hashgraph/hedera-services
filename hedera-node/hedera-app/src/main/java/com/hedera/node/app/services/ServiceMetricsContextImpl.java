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

package com.hedera.node.app.services;

import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.metrics.ServiceMetricsContext;
import com.hedera.node.app.spi.metrics.ServiceMetricsFactory;
import edu.umd.cs.findbugs.annotations.NonNull;

public record ServiceMetricsContextImpl(@NonNull String serviceName, @NonNull ServiceMetricsFactory factory)
        implements ServiceMetricsContext {
    @NonNull
    @Override
    public <T> T serviceMetrics(@NonNull final TransactionBody txBody, @NonNull Class<T> serviceInterface) {
        return factory.metrics(txBody, serviceInterface);
    }
}
