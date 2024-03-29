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

package com.swirlds.platform.components.appcomm;

import com.swirlds.common.wiring.component.InputWireLabel;
import com.swirlds.platform.state.signed.ReservedSignedState;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Responsible for notifying the app of the latest complete state.
 */
public interface LatestCompleteStateNotifier {
    /**
     * Submits a dispatch to the app containing the latest complete state.
     *
     * @param reservedSignedState the reserved signed state that is complete
     * @return a record containing the notification and a cleanup handler
     */
    @InputWireLabel("ReservedSignedState")
    @Nullable
    CompleteStateNotificationWithCleanup latestCompleteStateHandler(
            @NonNull final ReservedSignedState reservedSignedState);
}
