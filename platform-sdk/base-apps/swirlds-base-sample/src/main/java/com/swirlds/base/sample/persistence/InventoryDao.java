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

package com.swirlds.base.sample.persistence;

import com.swirlds.base.sample.domain.Inventory;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * in-memory simple data layer for Inventory
 */
public class InventoryDao {

    private static class InstanceHolder {
        private static final InventoryDao INSTANCE = new InventoryDao();
    }

    public static @NonNull InventoryDao getInstance() {
        return InstanceHolder.INSTANCE;
    }

    private static final Map<String, InnerItemExistence> INVENTORY = new ConcurrentHashMap<>();

    /**
     * Saves or updates the object
     */
    public @NonNull Inventory saveOrUpdate(final @NonNull Inventory existence) {
        Objects.requireNonNull(existence.itemId(), "itemId must not be null");
        final InnerItemExistence ie = this.internalFindWithBlockForUpdate(existence.itemId());
        if (ie == null) {
            INVENTORY.put(existence.itemId(), new InnerItemExistence(existence.amount()));
        } else {
            INVENTORY.put(existence.itemId(), new InnerItemExistence(existence.amount(), ie.lock));
            ie.lock.writeLock().unlock();
        }
        return existence;
    }

    public @Nullable Inventory findByItemId(final @NonNull String itemId) {
        final InnerItemExistence internalBalance = INVENTORY.get(itemId);
        if (internalBalance == null) {
            return null;
        }
        return new Inventory(itemId, internalBalance.amount);
    }

    /**
     * Retrieves the object with a lock for update. No other write operations can be performed against the object until
     * released.
     */
    public @Nullable Inventory findWithBlockForUpdate(final @NonNull String itemId) {
        final InnerItemExistence itemExistence = internalFindWithBlockForUpdate(itemId);
        if (itemExistence == null) {
            return null;
        }
        return new Inventory(itemId, itemExistence.amount);
    }

    /**
     * Release the write lock. Must be hold by the thread.
     */
    public void release(final @NonNull String itemId) {
        final InnerItemExistence itemExistence = INVENTORY.get(itemId);
        if (itemExistence != null) {
            itemExistence.lock.writeLock().unlock();
        }
    }

    public void deleteByItemId(final String itemId) {
        if (INVENTORY.get(itemId).lock.isWriteLockedByCurrentThread()) {
            INVENTORY.get(itemId).lock.writeLock().unlock();
        }
        INVENTORY.remove(itemId);
    }

    private @Nullable InnerItemExistence internalFindWithBlockForUpdate(final @NonNull String itemId) {
        final InnerItemExistence itemExistence = INVENTORY.get(itemId);
        if (itemExistence == null) {
            return null;
        }
        itemExistence.lock.writeLock().lock();
        return itemExistence;
    }

    private record InnerItemExistence(Integer amount, ReentrantReadWriteLock lock) {
        public InnerItemExistence(final Integer amount) {
            this(amount, new ReentrantReadWriteLock());
        }
    }
}
