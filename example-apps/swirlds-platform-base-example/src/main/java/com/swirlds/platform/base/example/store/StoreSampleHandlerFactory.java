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

package com.swirlds.platform.base.example.store;

import com.swirlds.platform.base.example.BaseContext;
import com.swirlds.platform.base.example.server.AdapterHandler;
import com.swirlds.platform.base.example.server.BaseExampleRestApiConfig;
import com.swirlds.platform.base.example.server.HttpHandlerDefinition;
import com.swirlds.platform.base.example.server.HttpHandlerFactory;
import com.swirlds.platform.base.example.store.metrics.ApplicationMetrics;
import com.swirlds.platform.base.example.store.metrics.BenchmarkMetrics;
import com.swirlds.platform.base.example.store.service.InventoryService;
import com.swirlds.platform.base.example.store.service.ItemService;
import com.swirlds.platform.base.example.store.service.OperationService;
import com.swirlds.platform.base.example.store.service.PurchaseService;
import com.swirlds.platform.base.example.store.service.SaleService;
import java.util.Set;

public class StoreSampleHandlerFactory implements HttpHandlerFactory {

    @Override
    public Set<HttpHandlerDefinition> initAndCreate(BaseContext context) {
        BenchmarkMetrics.registerMetrics(context.metrics());
        ApplicationMetrics.registerMetrics(context.metrics());
        InitialData.populate();

        final String basePath = context.configuration()
                .getConfigData(BaseExampleRestApiConfig.class)
                .basePath();
        return Set.of(
                new AdapterHandler<>(basePath + "/inventories", context, new InventoryService()),
                new AdapterHandler<>(basePath + "/operations", context, new OperationService(context)),
                new AdapterHandler<>(basePath + "/items", context, new ItemService()),
                new AdapterHandler<>(basePath + "/sales", context, new SaleService(context)),
                new AdapterHandler<>(basePath + "/purchases", context, new PurchaseService(context)));
    }
}
