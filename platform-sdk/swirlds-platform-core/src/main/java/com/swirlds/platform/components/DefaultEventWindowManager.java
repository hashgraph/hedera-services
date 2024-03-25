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

package com.swirlds.platform.components;

import com.swirlds.platform.consensus.NonAncientEventWindow;
import com.swirlds.platform.internal.ConsensusRound;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * The default implementation of the {@link EventWindowManager} interface.
 */
public class DefaultEventWindowManager implements EventWindowManager {

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public NonAncientEventWindow extractEventWindow(@NonNull final ConsensusRound round) {
        return round.getNonAncientEventWindow();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public NonAncientEventWindow updateEventWindow(@NonNull final NonAncientEventWindow eventWindow) {
        return eventWindow;
    }
}
