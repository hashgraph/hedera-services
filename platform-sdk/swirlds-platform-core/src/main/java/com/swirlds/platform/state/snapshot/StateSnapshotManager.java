/*
 * Copyright (C) 2018-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state.snapshot;

import com.swirlds.platform.listeners.StateWriteToDiskCompleteNotification;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.system.status.actions.PlatformStatusAction;
import com.swirlds.platform.system.status.actions.StateWrittenToDiskAction;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * This class is responsible for managing the signed state writing pipeline.
 */
public interface StateSnapshotManager {

    /**
     * Method to be called when a state needs to be written to disk in-band. An "in-band" write is part of normal
     * platform operations, whereas an out-of-band write is triggered due to a fault, or for debug purposes.
     * <p>
     * This method shouldn't be called if the state was written out-of-band.
     *
     * @param reservedSignedState the state to be written to disk. it is expected that the state is reserved prior to
     *                            this method call and this method will release the reservation when it is done
     * @return the result of the state saving operation, or null if the state was not saved
     */
    @Nullable
    StateSavingResult saveStateTask(@NonNull ReservedSignedState reservedSignedState);

    /**
     * Method to be called when a state needs to be written to disk out-of-band. An "in-band" write is part of normal
     * platform operations, whereas an out-of-band write is triggered due to a fault, or for debug purposes.
     *
     * @param request a request to dump a state to disk. it is expected that the state inside the request is reserved
     *                prior to this method call and this method will release the reservation when it is done
     */
    void dumpStateTask(@NonNull StateDumpRequest request);

    /**
     * Convert a {@link StateSavingResult} to a {@link StateWriteToDiskCompleteNotification}.
     *
     * @param result the result of the state saving operation
     * @return the notification
     */
    @NonNull
    default StateWriteToDiskCompleteNotification toNotification(@NonNull final StateSavingResult result) {
        return new StateWriteToDiskCompleteNotification(
                result.round(), result.consensusTimestamp(), result.freezeState());
    }

    /**
     * Extract the oldest minimum generation on disk from a {@link StateSavingResult}.
     *
     * @param result the result of the state saving operation
     * @return the oldest minimum generation on disk
     */
    @NonNull
    default Long extractOldestMinimumGenerationOnDisk(@NonNull final StateSavingResult result) {
        return result.oldestMinimumGenerationOnDisk();
    }

    /**
     * Convert a {@link StateSavingResult} to a {@link PlatformStatusAction}.
     *
     * @param result the result of the state saving operation
     * @return the action
     */
    @NonNull
    default PlatformStatusAction toStateWrittenToDiskAction(@NonNull final StateSavingResult result) {
        return new StateWrittenToDiskAction(result.round(), result.freezeState());
    }
}
