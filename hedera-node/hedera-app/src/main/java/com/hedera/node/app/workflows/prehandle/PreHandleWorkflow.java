/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.prehandle;

import com.hedera.node.app.state.HederaState;
import com.swirlds.common.system.events.Event;
import edu.umd.cs.findbugs.annotations.NonNull;

/** A workflow to pre-handle transactions. */
public interface PreHandleWorkflow {

    /**
     * Starts the pre-handle transaction workflow of the {@link Event}
     *
     * @param state the {@link HederaState} that is used
     * @param event the {@code Event} for which the workflow should be started
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    void start(@NonNull HederaState state, @NonNull Event event);
}
