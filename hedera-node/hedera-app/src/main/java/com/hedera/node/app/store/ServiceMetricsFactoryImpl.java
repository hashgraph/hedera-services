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

package com.hedera.node.app.store;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.ContractMetrics;
import com.hedera.node.app.service.contract.ContractService;
import com.hedera.node.app.services.ServiceScopeLookup;
import com.hedera.node.app.spi.RpcService;
import com.hedera.node.app.spi.metrics.ServiceMetrics;
import com.hedera.node.app.spi.metrics.ServiceMetricsFactory;
import com.hedera.node.app.spi.store.StoreFactory;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * TODO!!!
 * Factory for creating stores and service APIs. Default implementation of {@link StoreFactory}.
 */
@Singleton
public class ServiceMetricsFactoryImpl implements ServiceMetricsFactory {
    // todo the lookups in this class are wonky. I'd like to find a different way..
    private static final Map<String, Class<? extends ServiceMetrics>>
            METRICS_NAME_TO_INTERFACE_MAPPINGS = new HashMap<>();
    static {
        METRICS_NAME_TO_INTERFACE_MAPPINGS.put(ContractService.NAME, ContractMetrics.class);
    }

    private final ServiceScopeLookup serviceScopeLookup;
    private final Map<Class<? extends ServiceMetrics>, ServiceMetrics>
            metricsInterfaceToInstanceMappings = new HashMap<>();

    @Inject
    public ServiceMetricsFactoryImpl(@NonNull final ServiceScopeLookup serviceScopeLookup) {
        this.serviceScopeLookup = requireNonNull(serviceScopeLookup);
    }

    @Override
    public <T extends ServiceMetrics> void register(String serviceName, T metricsInstance) {
        var type = METRICS_NAME_TO_INTERFACE_MAPPINGS.get(serviceName);
        if (type != null) {
            metricsInterfaceToInstanceMappings.put(type, metricsInstance);
        }
    }

    @NonNull
    @Override
    public <T> T metrics(TransactionBody txBody, @NonNull final Class<T> metricsInterface) {
        requireNonNull(metricsInterface);

        final var serviceName = serviceScopeLookup.getServiceName(txBody);
        final var serviceInterface = METRICS_NAME_TO_INTERFACE_MAPPINGS.get(serviceName);
        if (Objects.equals(serviceInterface, metricsInterface)) {
            var instance = metricsInterface.cast(metricsInterfaceToInstanceMappings.get(serviceInterface));
            return instance != null
                    ? instance
                    //todo figure out this cast
                    : metricsInterface.cast(new RpcService.NoOpServiceMetrics().cast(metricsInterface));
        }

        throw new IllegalArgumentException("Requested metrics for external service, not permitted");
    }
}
