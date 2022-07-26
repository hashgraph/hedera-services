/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.ledger;

import static com.hedera.services.ledger.TransactionalLedger.MAX_ENTITIES_CONCEIVABLY_TOUCHED_IN_LEDGER_TXN;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.ledger.properties.BeanProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Represents a set of changes to one or more entities in a {@link TransactionalLedger}.
 *
 * <p><b>IMPORTANT: </b> the only changes represented are creations and mutations; we do not
 * currently have a use case that requires intercepting removals from a ledger.
 *
 * @param <K> the ledger id type
 * @param <A> the ledger entity type
 * @param <P> the enumerable family of properties
 */
public class EntityChangeSet<K, A, P extends Enum<P> & BeanProperty<A>> {
    // The change set is stored in three parallel lists to reduce object allocations; if the
    // entities
    // list has a null value at an index, it means the change at that index was a creation
    private final List<K> ids = new ArrayList<>(MAX_ENTITIES_CONCEIVABLY_TOUCHED_IN_LEDGER_TXN);
    private final List<A> entities =
            new ArrayList<>(MAX_ENTITIES_CONCEIVABLY_TOUCHED_IN_LEDGER_TXN);
    private final List<Map<P, Object>> changes =
            new ArrayList<>(MAX_ENTITIES_CONCEIVABLY_TOUCHED_IN_LEDGER_TXN);

    private int numRetainedChanges = 0;

    public K id(final int i) {
        return ids.get(i);
    }

    public A entity(final int i) {
        return entities.get(i);
    }

    public Map<P, Object> changes(final int i) {
        return changes.get(i);
    }

    public void clear() {
        ids.clear();
        changes.clear();
        entities.clear();
        numRetainedChanges = 0;
    }

    public int size() {
        return ids.size();
    }

    public int retainedSize() {
        return numRetainedChanges;
    }

    public void include(final K key, final A entity, final Map<P, Object> entityChanges) {
        ids.add(key);
        entities.add(entity);
        changes.add(entityChanges);
        numRetainedChanges++;
    }

    public void includeRemoval(final K key, final A entity) {
        ids.add(key);
        entities.add(entity);
        changes.add(null);
    }

    public void cacheEntity(final int i, final A entity) {
        entities.set(i, entity);
    }

    @VisibleForTesting
    List<K> getIds() {
        return ids;
    }

    List<A> getEntities() {
        return entities;
    }

    List<Map<P, Object>> getChanges() {
        return changes;
    }

    @Override
    public String toString() {
        return "EntityChangeSet{"
                + "ids="
                + ids
                + ", entities="
                + entities
                + ", changes="
                + changes
                + ", numRetainedChanges="
                + numRetainedChanges
                + '}';
    }
}
