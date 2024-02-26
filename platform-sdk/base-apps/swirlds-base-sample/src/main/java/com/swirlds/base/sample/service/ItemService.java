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

import com.swirlds.base.sample.domain.Inventory;
import com.swirlds.base.sample.domain.Item;
import com.swirlds.base.sample.persistence.InventoryDao;
import com.swirlds.base.sample.persistence.ItemDao;
import com.swirlds.base.sample.persistence.StockDao;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Controls items crud
 */
public class ItemService extends CrudService<Item> {

    private final @NonNull ItemDao dao;
    private final @NonNull InventoryDao inventoryDao;

    public ItemService() {
        super(Item.class);
        this.dao = ItemDao.getInstance();
        this.inventoryDao = InventoryDao.getInstance();
    }

    @Override
    public void delete(@NonNull final String itemId) {
        inventoryDao.findWithBlockForUpdate(itemId);
        StockDao.getInstance().deleteByItemId(itemId);
        dao.deleteById(itemId);
        inventoryDao.release(itemId);
        inventoryDao.deleteByItemId(itemId);
    }

    @NonNull
    @Override
    public Item create(@NonNull final Item body) {
        final Item save = dao.save(body);
        Objects.requireNonNull(save.id(), "id must not be null");
        if (!Objects.equals(body.id(), save.id())) { // Is creation
            inventoryDao.saveOrUpdate(new Inventory(save.id(), 0));
        }
        return save;
    }

    @NonNull
    @Override
    public Item retrieve(@NonNull final String key) {
        return dao.findById(key);
    }

    @NonNull
    @Override
    public List<Item> retrieveAll(@NonNull final Map<String, String> params) {
        return dao.findAll();
    }
}
