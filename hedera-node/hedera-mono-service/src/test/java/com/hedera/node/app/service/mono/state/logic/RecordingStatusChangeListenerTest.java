package com.hedera.node.app.service.mono.state.logic;

import com.hedera.node.app.service.mono.context.StateChildren;
import com.swirlds.common.notification.listeners.PlatformStatusChangeListener;
import com.swirlds.common.notification.listeners.PlatformStatusChangeNotification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.swirlds.common.system.PlatformStatus.ACTIVE;
import static com.swirlds.common.system.PlatformStatus.FREEZE_COMPLETE;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RecordingStatusChangeListenerTest {
    @Mock
    private StateChildren stateChildren;
    @Mock
    private PlatformStatusChangeListener delegate;
    @Mock
    private PlatformStatusChangeNotification platformStatusChangeNotification;

    private RecordingStatusChangeListener subject;

    @BeforeEach
    void setup() {
        subject = new RecordingStatusChangeListener(stateChildren, delegate);
    }

    @Test
    void alwaysNotifiesDelegate() {
        given(platformStatusChangeNotification.getNewStatus()).willReturn(ACTIVE);
        subject.notify(platformStatusChangeNotification);
        verify(delegate).notify(platformStatusChangeNotification);
    }

    @Test
    void onlyRecordsChildDataOnFreezeComplete() {
        given(platformStatusChangeNotification.getNewStatus()).willReturn(FREEZE_COMPLETE);
        subject.notify(platformStatusChangeNotification);
        verify(delegate).notify(platformStatusChangeNotification);
    }
}
