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

import com.swirlds.platform.base.example.ext.BaseContext;
import com.swirlds.platform.base.example.server.CrudHandler;
import com.swirlds.platform.base.example.server.HttpHandlerDefinition;
import com.swirlds.platform.base.example.server.HttpHandlerRegistry;
import com.swirlds.platform.base.example.store.metrics.StoreExampleMetrics;
import com.swirlds.platform.base.example.store.service.InventoryService;
import com.swirlds.platform.base.example.store.service.ItemService;
import com.swirlds.platform.base.example.store.service.OperationService;
import com.swirlds.platform.base.example.store.service.PurchaseService;
import com.swirlds.platform.base.example.store.service.SaleService;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/**
 * Creates all HttpHandlers to expose Store-Rest-Api operations
 */
public class StoreExampleHandlerRegistry implements HttpHandlerRegistry {

    @NonNull
    @Override
    public Set<HttpHandlerDefinition> handlers(@NonNull final BaseContext context) {

        StoreExampleMetrics.registerMetrics(context.metrics());
        InitialData.populate();
        return Set.of(
                new CrudHandler<>("store/inventories", context, new InventoryService()),
                new CrudHandler<>("store/operations", context, new OperationService(context)),
                new CrudHandler<>("store/items", context, new ItemService()),
                new CrudHandler<>("store/sales", context, new SaleService(context)),
                new CrudHandler<>("store/purchases", context, new PurchaseService(context)));
    }
}
