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

package com.swirlds.platform.test.gui;

import com.swirlds.platform.event.PlatformEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * Interface for classes that provide events for the GUI
 */
public interface GuiEventProvider {
    /**
     * Provide a list of events
     *
     * @param numberOfEvents the number of events to provide
     * @return the list of events
     */
    @NonNull
    List<PlatformEvent> provideEvents(final int numberOfEvents);

    /**
     * Reset the provider
     */
    void reset();
}
