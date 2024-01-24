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

package com.swirlds.platform.gossip;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.platform.consensus.NonAncientEventWindow;
import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.eventhandling.EventConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the current {@link com.swirlds.platform.consensus.NonAncientEventWindow}. Is used by gossip to determine what
 * is ancient and what is not, and to determine if one peer is behind the other.
 * <p>
 * This is a short term solution for sending the event window to gossip, since gossip is not currently in the wiring
 * framework. Once gossip is properly integrated into the wiring framework, this class should be removed.
 */
public class GossipEventWindowNexus {

    private final AtomicReference<NonAncientEventWindow> eventWindow = new AtomicReference<>();

    public GossipEventWindowNexus(@NonNull final PlatformContext platformContext) {
        final AncientMode ancientMode = platformContext
                .getConfiguration()
                .getConfigData(EventConfig.class)
                .getAncientMode();
        eventWindow.set(NonAncientEventWindow.getGenesisNonAncientEventWindow(ancientMode));
    }

    /**
     * Get the current event window.
     *
     * @return the current event window
     */
    @NonNull
    public NonAncientEventWindow getEventWindow() {
        return eventWindow.get();
    }

    /**
     * Set the current event window.
     *
     * @param eventWindow the new event window
     */
    public void setEventWindow(@NonNull final NonAncientEventWindow eventWindow) {
        this.eventWindow.set(eventWindow);
    }
}
