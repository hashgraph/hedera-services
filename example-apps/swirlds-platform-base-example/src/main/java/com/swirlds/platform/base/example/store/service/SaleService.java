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

package com.swirlds.platform.base.example.store.service;

import com.swirlds.platform.base.example.ext.BaseContext;
import com.swirlds.platform.base.example.server.CrudService;
import com.swirlds.platform.base.example.server.DataTransferUtils;
import com.swirlds.platform.base.example.store.domain.Operation;
import com.swirlds.platform.base.example.store.domain.Operation.OperationDetail;
import com.swirlds.platform.base.example.store.domain.OperationType;
import com.swirlds.platform.base.example.store.domain.Sale;
import com.swirlds.platform.base.example.store.domain.StockHandlingMode;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Objects;

/**
 * Creates Sales as a DEDUCTION
 */
public class SaleService extends CrudService<Sale> {

    private final BaseContext context;
    private final OperationService operationService;

    public SaleService(@NonNull final BaseContext context) {
        super(Sale.class);
        this.context = Objects.requireNonNull(context, "context cannot be null");
        this.operationService = new OperationService(context);
    }

    @NonNull
    @Override
    public Sale create(@NonNull final Sale sale) {
        final StockHandlingMode mode = Objects.requireNonNullElse(
                sale.mode(), context.configuration().getValue("sale.preferredStockHandling", StockHandlingMode.class));
        final OperationDetail operationDetail =
                new OperationDetail(sale.itemId(), sale.amount(), sale.salePrice(), mode);
        final Operation operation =
                operationService.create(new Operation(null, List.of(operationDetail), -1L, OperationType.DEDUCTION));
        return new Sale(
                operation.uuid(),
                sale.itemId(),
                sale.amount(),
                sale.salePrice(),
                mode,
                DataTransferUtils.fromEpoc(operation.timestamp()));
    }
}
