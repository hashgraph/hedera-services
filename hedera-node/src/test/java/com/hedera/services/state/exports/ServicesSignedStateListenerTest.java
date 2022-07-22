package com.hedera.services.state.exports;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.services.ServicesState;
import com.hedera.services.context.CurrentPlatformStatus;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.PlatformStatus;
import com.swirlds.common.system.state.notifications.NewSignedStateNotification;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServicesSignedStateListenerTest {
    private final Instant consensusNow = Instant.ofEpochSecond(1_234_567L, 890);
    private final NodeId selfId = new NodeId(false, 123L);
    @Mock private NewSignedStateNotification notice;
    @Mock private CurrentPlatformStatus currentPlatformStatus;
    @Mock private ServicesState signedState;
    @Mock private BalancesExporter balancesExporter;

    private ServicesSignedStateListener subject;

    @BeforeEach
    void setUp() {
        subject = new ServicesSignedStateListener(currentPlatformStatus, balancesExporter, selfId);
    }

    @Test
    void exportsIfTime() {
        given(notice.getSwirldState()).willReturn(signedState);
        given(notice.getConsensusTimestamp()).willReturn(consensusNow);
        given(balancesExporter.isTimeToExport(consensusNow)).willReturn(true);
        given(currentPlatformStatus.get()).willReturn(PlatformStatus.ACTIVE);

        subject.notify(notice);

        verify(balancesExporter).exportBalancesFrom(signedState, consensusNow, selfId);
    }

    @Test
    void justLogsIfFreezeCompleteAndNotTimeToExport() throws NoSuchAlgorithmException {
        given(notice.getSwirldState()).willReturn(signedState);
        given(currentPlatformStatus.get()).willReturn(PlatformStatus.FREEZE_COMPLETE);

        subject.notify(notice);

        verify(signedState).logSummary();
    }
}
