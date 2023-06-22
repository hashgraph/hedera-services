/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.system.status;

import com.swirlds.base.state.Startable;
import com.swirlds.base.state.Stoppable;
import com.swirlds.common.system.status.actions.PlatformStatusAction;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Interface for a platform status state machine that processes {@link PlatformStatusAction}s
 */
public interface PlatformStatusStateMachine extends Startable, Stoppable {
    /**
     * Process a platform status action.
     * <p>
     * Repeated calls of this method cause the platform state machine to be traversed
     *
     * @param action the action to process
     */
    void processStatusAction(@NonNull final PlatformStatusAction action);
}
