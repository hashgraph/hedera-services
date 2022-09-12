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
package com.hedera.services.ledger.properties;

import com.hedera.services.ledger.PropertyChangeObserver;
import java.util.Map;

/**
 * Minimal implementation of a helper that manages summary changesets. An extension point for
 * possible future performance optimizations.
 *
 * @param <A> the type of account being changed.
 * @param <P> the property family whose changesets are to be summarized.
 */
public final class ChangeSummaryManager<A, P extends Enum<P> & BeanProperty<A>> {
    /**
     * Updates the changeset summary for the given property to the given value.
     *
     * @param changes the total changeset summary so far
     * @param property the property in the family whose changeset should be updated
     * @param value the new value that summarizes the changeset
     */
    public void update(final Map<P, Object> changes, final P property, final Object value) {
        changes.put(property, value);
    }

    /**
     * Flush a changeset summary to a given object.
     *
     * @param changes the summary of changes made to the relevant property family
     * @param account the account to receive the net changes
     */
    public void persist(final Map<P, Object> changes, final A account) {
        changes.forEach((key, value) -> key.setter().accept(account, value));
    }

    /**
     * Flush a changeset summary to a given object, notifying the given observer of each change.
     *
     * @param id the id to communicate to the observer
     * @param changes the summary of changes made to the relevant property family.
     * @param account the account to receive the net changes
     * @param changeObserver the observer to be notified of the changes
     * @param <K> the type of id used to identify this account
     */
    public <K> void persistWithObserver(
            final K id,
            final Map<P, Object> changes,
            final A account,
            final PropertyChangeObserver<K, P> changeObserver) {
        changes.forEach(
                (property, newValue) -> {
                    property.setter().accept(account, newValue);
                    changeObserver.newProperty(id, property, newValue);
                });
    }
}
