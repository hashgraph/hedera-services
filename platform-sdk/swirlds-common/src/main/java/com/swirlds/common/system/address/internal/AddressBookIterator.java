/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.system.address.internal;

import com.swirlds.common.system.address.Address;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

/**
 * An iterator that walks over entries in an address book.
 */
public class AddressBookIterator implements Iterator<Address> {

    private final Iterator<Long> orderedNodeIds;
    private final Map<Long, Address> addresses;

    public AddressBookIterator(final Iterator<Long> orderedNodeIds, final Map<Long, Address> addresses) {
        this.orderedNodeIds = orderedNodeIds;
        this.addresses = addresses;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasNext() {
        return orderedNodeIds.hasNext();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Address next() {
        return Objects.requireNonNull(addresses.get(orderedNodeIds.next()));
    }
}
