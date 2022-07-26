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
 * Defines a ledger type with minimal semantics for manipulating accounts and a given family of
 * their properties. It is presumed that accounts can be in either a saved state; or a transient
 * state, such as occurs in the middle of an transaction. The accessors make this distinction
 * explicit.
 *
 * @param <K> the type of an account id
 * @param <P> the type of the property family associated to an account
 */
public interface Ledger<K, P extends Enum<P>> {
    /**
     * Sets value of a given property to a given value for the specified account.
     *
     * @param id the id of the account to update.
     * @param property the property to change.
     * @param value the new value of the property.
     */
    void set(K id, P property, Object value);

    /**
     * Creates an new account with the given id and all default property values.
     *
     * @param id the id to use for the new account.
     */
    void create(K id);

    /**
     * Forgets everything about the account with the given id.
     *
     * @param id the id of the account to be forgotten.
     */
    void destroy(K id);

    /**
     * Gets the current property value of the specified account. This value need not be persisted to
     * a durable backing store.
     *
     * @param id the id of the relevant account.
     * @param property which property to fetch.
     * @return the value of the property.
     */
    Object get(K id, P property);

    /**
     * Indicates whether an account is present (in either a saved or transient state---either is
     * considered extant).
     *
     * @param id the id of the relevant account.
     * @return whether the account is present.
     */
    boolean exists(K id);

    /**
     * Indicates whether an account is present solely in a transient state.
     *
     * @param id the id of the relevant account.
     * @return whether the account has no saved state, only transient.
     */
    boolean existsPending(K id);
}
