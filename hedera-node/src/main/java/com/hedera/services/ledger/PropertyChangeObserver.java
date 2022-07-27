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
package com.hedera.services.ledger;

/**
 * Defines a type able to receive information about changes to properties; used by {@link
 * TransactionalLedger} as a commit interceptor to allow a parent {@link
 * com.hedera.services.store.contracts.AbstractLedgerWorldUpdater} to keep its {@code
 * updateAccounts} map in sync with changes made by an HTS precompile.
 *
 * @param <K> the key that identifies a changeable entity
 * @param <P> the enumerable family of changeable properties
 */
@FunctionalInterface
public interface PropertyChangeObserver<K, P extends Enum<P>> {
    /**
     * Called to notify the observer that the entity with the given id has had its property of the
     * given type changed.
     *
     * @param id the changed entity
     * @param property the target property
     * @param newValue the new value of that property
     */
    void newProperty(K id, P property, Object newValue);
}
