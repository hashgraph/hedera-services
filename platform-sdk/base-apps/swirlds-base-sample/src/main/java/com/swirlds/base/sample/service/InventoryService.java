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

package com.swirlds.base.sample.service;

import com.swirlds.base.sample.domain.DetailedInventory;
import com.swirlds.base.sample.domain.DetailedInventory.Movement;
import com.swirlds.base.sample.domain.DetailedInventory.StockDetail;
import com.swirlds.base.sample.domain.Inventory;
import com.swirlds.base.sample.domain.Item;
import com.swirlds.base.sample.domain.Operation;
import com.swirlds.base.sample.domain.Operation.OperationDetail;
import com.swirlds.base.sample.internal.DataTransferUtils;
import com.swirlds.base.sample.persistence.InventoryDao;
import com.swirlds.base.sample.persistence.ItemDao;
import com.swirlds.base.sample.persistence.OperationDao;
import com.swirlds.base.sample.persistence.OperationDao.Criteria;
import com.swirlds.base.sample.persistence.StockDao;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * Controls balance operations
 */
public class InventoryService extends CrudService<DetailedInventory> {
    private final @NonNull ItemDao itemDao;
    private final @NonNull InventoryDao inventoryDao;
    private final @NonNull StockDao stockDao;
    private final @NonNull OperationDao operationDao;

    public InventoryService() {
        super(DetailedInventory.class);
        this.itemDao = ItemDao.getInstance();
        this.stockDao = StockDao.getInstance();
        this.inventoryDao = InventoryDao.getInstance();
        this.operationDao = OperationDao.getInstance();
    }

    @Nullable
    @Override
    public DetailedInventory retrieve(@NonNull final String itemId) {
        final Item item = itemDao.findById(itemId);
        if (item == null) {
            return null;
        }
        final Inventory inventory = Objects.requireNonNull(
                inventoryDao.findByItemId(itemId), "There should be an Inventory entry for the item:" + itemId);

        final List<StockDetail> stocks = stockDao.getAll(itemId).stream()
                .map(s -> new StockDetail(
                        s.unitaryPrice(),
                        DataTransferUtils.fromEpoc(s.timestamp()),
                        s.remaining().get()))
                .toList();

        final List<Operation> byCriteria = operationDao.findByCriteria(new Criteria(
                null,
                itemId,
                Date.from(LocalDateTime.now().minusMonths(1).toInstant(ZoneOffset.UTC)),
                null,
                null,
                null));

        List<Movement> movements = new ArrayList<>(byCriteria.size());
        for (Operation op : byCriteria) {
            for (OperationDetail detail : op.details()) {
                assert op.uuid() != null; // never fails
                if (detail.itemId().equals(itemId)) {
                    movements.add(
                            new Movement(new Date(op.timestamp()), op.type().apply(0, detail.amount()), op.uuid()));
                }
            }
        }
        return new DetailedInventory(item, inventory.amount(), stocks, movements);
    }
}
