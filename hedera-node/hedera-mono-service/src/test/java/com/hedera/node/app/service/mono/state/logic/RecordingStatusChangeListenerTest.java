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

package com.hedera.node.app.service.mono.state.logic;

import static com.swirlds.common.system.PlatformStatus.ACTIVE;
import static com.swirlds.common.system.PlatformStatus.FREEZE_COMPLETE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.mono.context.StateChildren;
import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.replay.ReplayAssetRecording;
import com.swirlds.common.notification.listeners.PlatformStatusChangeListener;
import com.swirlds.common.notification.listeners.PlatformStatusChangeNotification;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecordingStatusChangeListenerTest {
    @Mock
    private StateChildren stateChildren;

    @Mock
    private PlatformStatusChangeListener delegate;

    @Mock
    private PlatformStatusChangeNotification platformStatusChangeNotification;

    @Mock
    private ReplayAssetRecording assetRecording;

    @Mock
    private MerkleMapLike<EntityNum, MerkleTopic> topics;

    private RecordingStatusChangeListener subject;

    RecordingStatusChangeListenerTest() {}

    @BeforeEach
    void setup() {
        subject = new RecordingStatusChangeListener(stateChildren, assetRecording, delegate);
    }

    @Test
    void alwaysNotifiesDelegate() {
        given(platformStatusChangeNotification.getNewStatus()).willReturn(ACTIVE);
        subject.notify(platformStatusChangeNotification);
        verify(delegate).notify(platformStatusChangeNotification);
    }

    @Test
    void recordsChildDataOnFreezeComplete() {
        given(platformStatusChangeNotification.getNewStatus()).willReturn(FREEZE_COMPLETE);
        subject.notify(platformStatusChangeNotification);
        verify(delegate).notify(platformStatusChangeNotification);
    }

    @Test
    void exportsAllTopics() {
        willAnswer(invocation -> {
                    final BiConsumer<EntityNum, MerkleTopic> action = invocation.getArgument(0);

                    return null;
                })
                .given(topics)
                .forEach(any());
    }
}
