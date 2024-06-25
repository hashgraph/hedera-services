/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.gui.hashgraph;

import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * Provides the {@code HashgraphGui} information it needs to render an image of the hashgraph
 */
public interface HashgraphGuiSource {

    /**
     * @return the maximum generation of all events this source has
     */
    long getMaxGeneration();

    /**
     * Get events to be displayed by the GUI
     *
     * @param startGeneration the start generation of events returned
     * @param numGenerations  the number of generations to be returned
     * @return an array of requested events
     */
    @NonNull
    List<EventImpl> getEvents(final long startGeneration, final int numGenerations);

    /**
     * Get the Address Book
     *
     * @return AddressBook
     */
    @NonNull
    AddressBook getAddressBook();

    /**
     * @return true if the source is ready to return data
     */
    boolean isReady();
}
