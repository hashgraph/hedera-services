/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.gui.hashgraph.internal;

import com.swirlds.platform.gui.GuiEventStorage;
import com.swirlds.platform.gui.hashgraph.HashgraphGuiSource;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Objects;

/**
 * A {@link HashgraphGuiSource} that retrieves events from a stream of events
 */
public class StandardGuiSource implements HashgraphGuiSource {

    private final AddressBook addressBook;
    private final GuiEventStorage eventStorage;

    /**
     * Constructor
     *
     * @param eventStorage stores information about events
     */
    public StandardGuiSource(@NonNull final AddressBook addressBook, @NonNull final GuiEventStorage eventStorage) {

        this.addressBook = Objects.requireNonNull(addressBook);
        this.eventStorage = Objects.requireNonNull(eventStorage);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getMaxGeneration() {
        return eventStorage.getMaxGeneration();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public List<EventImpl> getEvents(final long startGeneration, final int numGenerations) {
        return eventStorage.getNonAncientEvents();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public AddressBook getAddressBook() {
        return addressBook;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isReady() {
        return true;
    }
}
