/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.ledger.backing;

import java.util.Set;

/**
 * Defines a type that provides safe and unsafe access to a collection of accounts. ("Safe" implies
 * a defensive copy is returned from the accessor, while "unsafe" implies a reference to the element
 * in the underlying collection is returned).
 *
 * @param <K> the type of id used to index the collection.
 * @param <A> the type of entity stored in the collection.
 */
public interface BackingStore<K, A> {
    /**
     * Alerts this {@code BackingStore} it should reconstruct any auxiliary data structures based on
     * its underlying sources. Used in particular for reconnect.
     */
    default void rebuildFromSources() {
        /* No-op. */
    }

    /**
     * Gets a possibly mutable reference to the account with the specified id.
     *
     * @param id the id of the relevant account
     * @return a reference to the account, or null if it is missing
     */
    A getRef(K id);

    /**
     * Gets a reference to the account with the specified id which should not be mutated.
     *
     * @param id the id of the relevant account
     * @return a reference to the account
     */
    A getImmutableRef(K id);

    /**
     * Updates (or creates, if absent) the account with the given id to the accompanying account.
     *
     * @param id the id of the relevant account
     * @param entity the account that should have this id
     */
    void put(K id, A entity);

    /**
     * Frees the account with the given id for reclamation.
     *
     * @param id the id of the relevant account
     */
    void remove(K id);

    /**
     * Checks if the collection contains the account with the given id.
     *
     * @param id the account in question
     * @return a flag for existence
     */
    boolean contains(K id);

    /**
     * Returns the set of extant account ids. <b>This is a very computation-intensive operation.
     * <i>idSet</i> should only be called at state initialisation Avoid using this method in a
     * normal handleTransaction flow.</b>
     *
     * @return the set of extant account ids
     */
    Set<K> idSet();

    /**
     * Returns the count of extant entities stored in the correspondent collection
     *
     * @return the count of extant entities stored in the correspondent collection
     */
    long size();
}
