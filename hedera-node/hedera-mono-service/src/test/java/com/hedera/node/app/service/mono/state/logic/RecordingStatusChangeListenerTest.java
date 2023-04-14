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

import static com.hedera.node.app.service.mono.state.logic.RecordingStatusChangeListener.FINAL_TOPICS_ASSET;
import static com.swirlds.common.system.PlatformStatus.ACTIVE;
import static com.swirlds.common.system.PlatformStatus.FREEZE_COMPLETE;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.node.app.service.mono.context.StateChildren;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.replay.PbjLeafConverters;
import com.hedera.node.app.service.mono.utils.replay.ReplayAssetRecording;
import com.hedera.test.utils.SeededPropertySource;
import com.swirlds.common.notification.listeners.PlatformStatusChangeListener;
import com.swirlds.common.notification.listeners.PlatformStatusChangeNotification;
import com.swirlds.merkle.map.MerkleMap;
import java.util.SplittableRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecordingStatusChangeListenerTest {
    private static final int NUM_MOCK_TOPICS = 10;

    @Mock
    private StateChildren stateChildren;

    @Mock
    private PlatformStatusChangeListener delegate;

    @Mock
    private PlatformStatusChangeNotification platformStatusChangeNotification;

    @Mock
    private ReplayAssetRecording assetRecording;

    private MerkleMap<EntityNum, MerkleTopic> topics;

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
        givenSomeTopics();
        given(platformStatusChangeNotification.getNewStatus()).willReturn(FREEZE_COMPLETE);

        subject.notify(platformStatusChangeNotification);

        verify(delegate).notify(platformStatusChangeNotification);
        for (int i = 0; i < NUM_MOCK_TOPICS; i++) {
            final var topic = topics.get(EntityNum.fromLong(i));
            final var encodedTopic = PbjConverter.toB64Encoding(PbjLeafConverters.topicFromMerkle(topic), Topic.class);
            verify(assetRecording).appendPlaintextToAsset(FINAL_TOPICS_ASSET, encodedTopic);
        }
    }

    private void givenSomeTopics() {
        topics = new MerkleMap<>();
        final var r = new SplittableRandom(1_234_567L);
        final var source = new SeededPropertySource(r);
        for (int i = 0; i < NUM_MOCK_TOPICS; i++) {
            topics.put(EntityNum.fromLong(i), source.nextTopic());
        }
        given(stateChildren.topics()).willReturn(MerkleMapLike.from(topics));
    }
}
