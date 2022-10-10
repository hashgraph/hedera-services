/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.exports;

import static com.swirlds.common.system.PlatformStatus.FREEZE_COMPLETE;

import com.hedera.services.ServicesState;
import com.hedera.services.context.CurrentPlatformStatus;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.state.notifications.NewSignedStateListener;
import com.swirlds.common.system.state.notifications.NewSignedStateNotification;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ServicesSignedStateListener implements NewSignedStateListener {

    private final CurrentPlatformStatus currentPlatformStatus;
    private final BalancesExporter balancesExporter;
    private final NodeId nodeId;

    @Inject
    public ServicesSignedStateListener(
            final CurrentPlatformStatus currentPlatformStatus,
            final BalancesExporter balancesExporter,
            final NodeId nodeId) {
        this.currentPlatformStatus = currentPlatformStatus;
        this.balancesExporter = balancesExporter;
        this.nodeId = nodeId;
    }

    @Override
    public void notify(final NewSignedStateNotification notice) {
        final ServicesState signedState = notice.getSwirldState();
        if (currentPlatformStatus.get() == FREEZE_COMPLETE) {
            signedState.logSummary();
        }
        final var at = notice.getConsensusTimestamp();
        if (balancesExporter.isTimeToExport(at)) {
            balancesExporter.exportBalancesFrom(signedState, at, nodeId);
        }
    }
}
