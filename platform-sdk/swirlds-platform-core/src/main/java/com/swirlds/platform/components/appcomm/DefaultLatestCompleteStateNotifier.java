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

package com.swirlds.platform.components.appcomm;

import com.swirlds.platform.consensus.ConsensusConstants;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.system.state.notifications.NewSignedStateNotification;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Default implementation of {@link LatestCompleteStateNotifier}.
 */
public class DefaultLatestCompleteStateNotifier implements LatestCompleteStateNotifier {
    /**
     * The round of the latest state provided to the application
     */
    private long latestStateProvidedRound = ConsensusConstants.ROUND_UNDEFINED;

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public CompleteStateNotificationWithCleanup latestCompleteStateHandler(
            @NonNull final ReservedSignedState reservedSignedState) {
        if (reservedSignedState.get().getRound() <= latestStateProvidedRound) {
            // this state is older than the latest state provided to the application, no need to notify
            reservedSignedState.close();
            return null;
        }
        latestStateProvidedRound = reservedSignedState.get().getRound();
        final NewSignedStateNotification notification = new NewSignedStateNotification(
                reservedSignedState.get().getSwirldState(),
                reservedSignedState.get().getRound(),
                reservedSignedState.get().getConsensusTimestamp());

        return new CompleteStateNotificationWithCleanup(notification, r -> reservedSignedState.close());
    }
}
