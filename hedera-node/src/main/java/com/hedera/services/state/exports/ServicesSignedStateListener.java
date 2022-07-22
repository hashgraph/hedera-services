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
