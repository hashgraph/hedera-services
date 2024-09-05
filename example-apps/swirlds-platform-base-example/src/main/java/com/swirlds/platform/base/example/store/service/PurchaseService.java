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
import com.swirlds.platform.base.example.store.domain.Purchase;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * Creates Purchases
 */
public class PurchaseService extends CrudService<Purchase> {

    private final OperationService operationService;

    public PurchaseService(@NonNull final BaseContext context) {
        super(Purchase.class);
        this.operationService = new OperationService(context);
    }

    @NonNull
    @Override
    public Purchase create(@NonNull final Purchase purchase) {
        final OperationDetail operationDetail =
                new OperationDetail(purchase.itemId(), purchase.amount(), purchase.buyPrice(), null);
        final Operation operation =
                operationService.create(new Operation(null, List.of(operationDetail), -1L, OperationType.ADDITION));
        return new Purchase(
                operation.uuid(),
                purchase.itemId(),
                purchase.amount(),
                purchase.buyPrice(),
                DataTransferUtils.fromEpoc(operation.timestamp()));
    }
}
