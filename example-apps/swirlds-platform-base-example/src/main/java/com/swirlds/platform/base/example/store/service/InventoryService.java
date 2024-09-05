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

import com.swirlds.platform.base.example.server.CrudService;
import com.swirlds.platform.base.example.server.DataTransferUtils;
import com.swirlds.platform.base.example.store.domain.DetailedInventory;
import com.swirlds.platform.base.example.store.domain.DetailedInventory.Movement;
import com.swirlds.platform.base.example.store.domain.DetailedInventory.StockDetail;
import com.swirlds.platform.base.example.store.domain.Inventory;
import com.swirlds.platform.base.example.store.domain.Item;
import com.swirlds.platform.base.example.store.domain.Operation;
import com.swirlds.platform.base.example.store.domain.Operation.OperationDetail;
import com.swirlds.platform.base.example.store.persistence.InventoryDao;
import com.swirlds.platform.base.example.store.persistence.ItemDao;
import com.swirlds.platform.base.example.store.persistence.OperationDao;
import com.swirlds.platform.base.example.store.persistence.OperationDao.Criteria;
import com.swirlds.platform.base.example.store.persistence.StockDao;
import edu.umd.cs.findbugs.annotations.NonNull;
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

    private final ItemDao itemDao;
    private final InventoryDao inventoryDao;
    private final StockDao stockDao;
    private final OperationDao operationDao;

    public InventoryService() {
        super(DetailedInventory.class);
        this.itemDao = ItemDao.getInstance();
        this.stockDao = StockDao.getInstance();
        this.inventoryDao = InventoryDao.getInstance();
        this.operationDao = OperationDao.getInstance();
    }

    @NonNull
    @Override
    public DetailedInventory retrieve(@NonNull final String itemId) {
        final Item item = itemDao.findById(itemId);
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
